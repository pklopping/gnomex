/*
 * $Id: FDTWriterSession.java,v 1.1 2012-10-29 22:29:37 HCI\rcundick Exp $
 */
package lia.util.net.copy;

import gui.FdtMain;
import java.io.File;
import java.net.InetAddress;
import java.util.Date;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

import lia.util.net.common.Config;
import lia.util.net.common.FileChannelProvider;
import lia.util.net.common.StoragePathDecoder;
import lia.util.net.common.Utils;
import lia.util.net.copy.disk.DiskWriterManager;
import lia.util.net.copy.disk.ResumeManager;
import lia.util.net.copy.filters.Postprocessor;
import lia.util.net.copy.filters.Preprocessor;
import lia.util.net.copy.filters.ProcessorInfo;
import lia.util.net.copy.transport.ControlChannel;
import lia.util.net.copy.transport.CtrlMsg;
import lia.util.net.copy.transport.FDTProcolException;
import lia.util.net.copy.transport.FDTSessionConfigMsg;
import lia.util.net.copy.transport.TCPSessionReader;

import gui.GUIFileStatus;
import gui.Log;
import gui.ShowMessageDialog;
import java.io.IOException;

/**
 * The Writer session ...
 * 
 * @author ramiro
 */
public class FDTWriterSession extends FDTSession implements FileBlockConsumer {

    private static final Log logger = Log.getLoggerInstance();

    //private static final GUIFileStatus guiFileStatus = GUIFileStatus.getGUIFileStatusInstance();

    private static final ResumeManager     resumeManager = ResumeManager.getInstance();

    private static final Config            config        = Config.getInstance();

    private static final DiskWriterManager dwm           = DiskWriterManager.getInstance();

    private String                         destinationDir;

    private String[]                       fileList;

    private ProcessorInfo                  processorInfo;

    private final AtomicBoolean finalCleaupExecuted = new AtomicBoolean(false);
    private final AtomicBoolean finishNotifiedExecuted = new AtomicBoolean(false);

    public FDTWriterSession() throws Exception {
        super(FDTSession.CLIENT);
        dwm.addSession(this);
        sendInitConf();
        this.monID = config.getMonID();
    }

    /**
     * REMOTE SESSION
     * 
     * @param fileList
     * @throws Exception
     */
    public FDTWriterSession(ControlChannel cc) throws Exception {
        super(cc, FDTSession.SERVER);
        dwm.addSession(this);
        this.monID = (String) cc.remoteConf.get("-monID");
    }

    public void setControlChannel(ControlChannel controlChannel) {
        this.controlChannel = controlChannel;
    }

    public void notifyWriterDown(int partitionID) {
    }

    private void notifySessionFinished() {

        if(finishNotifiedExecuted.compareAndSet(false, true)) {
            StringBuilder downNotif = null;

            try {
                if(downMessage() != null && downCause() != null) {

                    downNotif = new StringBuilder();

                    if(downMessage() != null) {
                        downNotif.append("Down message: ").append(downMessage()).append("\n");
                    }
                    if(downCause() != null) {
                        downNotif.append("Down cause:\n").append(Utils.getStackTrace(downCause())).append("\n");
                    }
                }
            } catch(Throwable t1) {
                if(logger.isLoggable(Level.FINE)) {
                    logger.log(Level.FINE, "[ FDTWriterSession ] [ notifySessionFinished ]  Got exception building the remote notify message", t1);
                }
            }

            try {
                controlChannel.sendCtrlMessage(new CtrlMsg(CtrlMsg.END_SESSION, (downNotif == null)?null:downNotif.toString()));
            } catch(Throwable t1) {
                logger.log(Level.WARNING, " [ FDTWriterSession ] [ notifySessionFinished ] got exception sending END_SESSION message", t1);
            }
        }
    }

    private void finalCleanup() {

        if(finalCleaupExecuted.compareAndSet(false, true)) {

            if(logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, "\n\n [ FDTWriterSession ] [ finalCleanup ] \n\n ");
            }
            
            try {
                notifySessionFinished();
            }catch(Throwable ignore) {}
            
            try {
                
                StringBuilder sb = new StringBuilder();
                sb.append("\n\nFDTWriterSession ( ").append(sessionID);
                if(monID != null) {
                    sb.append(" / ").append(monID);
                }
                sb.append(" ) final stats:");
                sb.append("\n Started: ").append(new Date(startTimeMillis));
                sb.append("\n Ended:   ").append(new Date());
                sb.append("\n Transfer period:   ").append(Utils.getETA(TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startTimeNanos)));
                sb.append("\n TotalBytes: ").append(getTotalBytes());
                if(transportProvider != null) {
                    sb.append("\n TotalNetworkBytes: ").append(transportProvider.getUtilBytes());
                    try {
                        if(!Utils.updateTotalWriteContor(transportProvider.getUtilBytes())) {
                            if(logger.isLoggable(Level.FINEST)) {
                                logger.log(Level.FINEST, " [ FDTWriterSession ] Unable to update the contor in the update file.");
                            }
                        }
                    }catch(Throwable tu) {
                        if(logger.isLoggable(Level.FINEST)) {
                            logger.log(Level.FINEST, " [ FDTWriterSession ] Unable to update the contor in the update file. Cause: ", tu);
                        }
                    } finally {
                        transportProvider.close(downMessage(), downCause());
                    }
                } else {
                    sb.append("\n TotalNetworkBytes: 0");
                }
                sb.append("\n Exit Status: ").append((downCause() == null && downMessage() == null)?"OK":"Not OK");
                sb.append("\n");
                logger.log(sb.toString());
            } catch(Throwable t) {
                logger.log(Level.WARNING, "[ FDTWriterSession ] [ finalCleanup ] [ HANDLED ] Exception getting final statistics. Smth went dreadfully wrong!", t);
            }

            try {
                dwm.removeSession(this, downMessage(), downCause());
            }catch(Throwable _) {}
            
            try {
                for(final FileSession fileSession: fileSessions.values()) {
                    try {
                        fileSession.close(downMessage(), downCause());
                    }catch(Throwable ign) {}
                }
            }catch(Throwable _) {}
            
            try {
                doPostProcessing();
            } catch (Throwable t1) {
                logger.log(Level.WARNING, " Got exception in postProcessing", t1);
            }
            
            try {
                if(transportProvider != null) {
                    transportProvider.close(downMessage(), downCause());
                }
            }catch(Throwable _) {}

            try {
                if(controlChannel != null) {
                    controlChannel.close(downMessage(), downCause());
                }
            }catch(Throwable _) {}
            
            try {
                FDTSessionManager.getInstance().finishSession(sessionID, downMessage(), downCause());
            } catch (Throwable ignore) {
            }
        }
    }
    @Override
    protected void internalClose() throws Exception {
        
        if(logger.isLoggable(Level.FINER)) {
            Thread.dumpStack();
            logger.log(Level.FINER, " [ FDTWriterSession ] enters internalClose downMsg: " + downMessage() + " ,  downCause: " + downCause());
            Thread.dumpStack();

        }
        
        try {
            super.internalClose();
        } catch(Throwable t) {
            logger.log(Level.WARNING, " [ FDTWriterSession ] [ HANDLED ] internalClose exception in base class.", t);
        }

        final String downMessage = downMessage();
        final Throwable downCause = downCause();


        if (downMessage == null && downCause == null) {
            checkFinished(null);
        } else {
            final String downLogMsg = (downMessage == null) ? "N/A" : downMessage;
            logger.log(Level.INFO, "\nThe FDTWriterSession ( " + sessionID + " ) finished with error(s). Cause: " + downLogMsg, downCause());
            finalCleanup();
        }

    }

    private void sendInitConf() throws Exception {
        FDTSessionConfigMsg sccm = new FDTSessionConfigMsg();

        sccm.destinationDir = config.getDestinationDir();
        sccm.fileLists = config.getFileList();
        sccm.remappedFileLists = config.getRemappedFileList();
        sccm.recursive = config.isRecursive();
        this.destinationDir = sccm.destinationDir;

        controlChannel.sendCtrlMessage(new CtrlMsg(CtrlMsg.INIT_FDTSESSION_CONF, sccm));
        setCurrentState(INIT_CONF_SENT);
    }

    private void sendFinishedSessions() throws Exception {
        controlChannel.sendCtrlMessage(new CtrlMsg(CtrlMsg.FINAL_FDTSESSION_CONF, finishedSessions.toArray(new UUID[finishedSessions.size()])));
        setCurrentState(FINAL_CONF_SENT);
    }

    @Override
    public void handleInitFDTSessionConf(CtrlMsg ctrlMsg) throws Exception {
        // this should be never called ( for the moment ... )
        FDTProcolException fpe = new FDTProcolException("Illegal message INIT_FDT_CONF in WriterSesssion");
        fpe.fillInStackTrace();
        throw fpe;
    }

    @Override
    public void handleFinalFDTSessionConf(CtrlMsg ctrlMsg) throws Exception {
        FDTSessionConfigMsg sccm = (FDTSessionConfigMsg) ctrlMsg.message;

        this.destinationDir = sccm.destinationDir;

        // re-assign destination directory if transferring to storage
        StoragePathDecoder spd = new StoragePathDecoder(sccm.destinationDir, "", "");
        if (spd.hasStorageInfo()) {
            if (config.storageParams() == null) {
                logger.log(Level.SEVERE, "Unable to transfer to storage: " + "Storage configuration is not found.");
            } else {
                this.destinationDir = config.storageParams().localFileDir();
                logger.log(Level.WARNING, "Destination directory has been " + "changed from " + sccm.destinationDir
                           + " to " + this.destinationDir);
            }
        }

        this.fileList = new String[sccm.fileLists.length];
        System.arraycopy(sccm.fileLists, 0, this.fileList, 0, this.fileList.length);

        boolean shouldReplace = false;
        final char remoteCharSeparator = ((String) controlChannel.remoteConf.get("file.separator")).charAt(0);
        if (File.separatorChar == '/') {
            if (remoteCharSeparator == '\\') {
                shouldReplace = true;
            }
        }

        // first build the map ... then check for already transfered files
        int fCount = sccm.fileIDs.length;

        //check for temp file
        boolean noTmp = false;
        if(config.isNoTmpFlagSet() || controlChannel.remoteConf.get("-notmp") != null) {
            noTmp = true;
        }
        boolean noLock = false;
        if(config.isNoLockFlagSet() || controlChannel.remoteConf.get("-nolock") != null || controlChannel.remoteConf.get("-nolocks") != null) {
            noLock = true;
        }
        
        final FileChannelProvider fcp = Config.getInstance().getFileChannelProviderFactory().newWriterFileChannelProvider(this);

        //check destination dir on server, is this requested transfer allowed?
        if (role == SERVER && config.getRestrictedTransferDirectories() != null){
                if (config.authorizedTransfer(new File(destinationDir)) == false) throw new IOException("Unauthorized access request to "+destinationDir);
        }


        for (int i = 0; i < fCount; i++) {
            final String fName = (sccm.remappedFileLists == null || sccm.remappedFileLists[i] == null)?sccm.fileLists[i]:sccm.remappedFileLists[i];
            FileWriterSession fws = new FileWriterSession(sccm.fileIDs[i], this, this.destinationDir
                                                          + File.separator
                                                          + ((shouldReplace) ? fName.replace(remoteCharSeparator, File.separatorChar)
                                                                  : fName), sccm.fileSizes[i], sccm.lastModifTimes[i], isLoop, writeMode, noTmp, noLock, fcp);
            fileSessions.put(fws.sessionID, fws);
            if(!FdtMain.isIsServerMode())
            {
                GUIFileStatus guiFileStatus = GUIFileStatus.getGUIFileStatusInstance();
                guiFileStatus.initFileInfo(fName,fws.sessionID.toString(),"Server",this.destinationDir +"/"+ fName,fws.sessionSize );
                guiFileStatus.initTable("Server", this.destinationDir +"/"+ fName, fws.sessionID.toString(), fws.sessionSize);
            }
            

            setSessionSize(sessionSize() + fws.sessionSize());
        }

        for (final FileSession fws : fileSessions.values()) {
            if (resumeManager.isFinished(fws)) {
                logger.log(Level.FINER, "\n\n\n ====> [ FDTWriterSession ] the file session " + fws.sessionID + " is Finished!");
                addAndGetUtilBytes(fws.sessionSize);
                addAndGetTotalBytes(fws.sessionSize);
                super.finishFileSession(fws.sessionID, null);
                
            } else {
                logger.log(Level.FINER, "\n\n\n ====> [ FDTWriterSession ] <====== the file session " + fws.sessionID + " is NOT Finished!  <============ ");
            }
        }
        
        final long sTime = System.nanoTime();
        boolean bPreProccessing = false;

        try {
            bPreProccessing = doPreprocess();
        } catch (Throwable tPrepProcess) {
            logger.log(Level.WARNING, "Got exception preprocessing", tPrepProcess);
        }

        if (bPreProccessing) {
            logger.log(Level.INFO, "Preprocessing took: " + (TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - sTime)) + " ms");
        }

        buildPartitionMap();
        sendFinishedSessions();
        
        // I will start the transport writer and notify the FDTReader Peer
        if (role == SERVER) {
            // just to be sure
            if (transportProvider == null) {
                transportProvider = new TCPSessionReader(this, this);
            } else {
                throw new FDTProcolException(" Non null transport provider !");
            }
        } else if (role == CLIENT) {
            transportProvider = new TCPSessionReader(this,
                                                     this,
                                                     InetAddress.getByName(config.getHostName()),
                                                     config.getPort(),
                                                     config.getSockNum());
        }

        // Notify the reader that he can start to send the data
        controlChannel.sendCtrlMessage(new CtrlMsg(CtrlMsg.START_SESSION, null));

        setCurrentState(START_SENT);

        checkFinished(null);
    }

    public long getSize() {
        return sessionSize();
    }

    @Override
    public void handleStartFDTSession(CtrlMsg ctrlMsg) throws Exception {
        if (role == CLIENT) {
            if (transportProvider == null) {
                transportProvider = new TCPSessionReader(this,
                                                         this,
                                                         InetAddress.getByName(config.getHostName()),
                                                         config.getPort(),
                                                         config.getSockNum());
            }
        }

        setCurrentState(TRANSFERING);
        transportProvider.startTransport(true);
        
    }

    @SuppressWarnings("unchecked")
    @Override
    public void handleEndFDTSession(CtrlMsg ctrlMsg) throws Exception {

        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER, "\n\n\n\n\n\n ---------------- [ FDTWriterSession ] handleEndFDTSession. Msg: " + ctrlMsg.message);
        }

        String remoteDownMsg = null;
        if (ctrlMsg.message != null) {
            if (ctrlMsg.message instanceof TreeMap) {
                logger.log(Level.INFO, "[ FDTWriterSession ] Remote FDTReaderSession for session [ " + sessionID + " ] finished ok. Waiting for our side to finish.");
                // the md5 sums
                TreeMap<UUID, byte[]> md5Sums = (TreeMap<UUID, byte[]>)ctrlMsg.message;

                if (md5Sums != null && md5Sums.size() > 0) {
                    StringBuilder sb = new StringBuilder();
                    sb.append("\n\n===\tRemote MD5 Sums\t===");
                    for (Map.Entry<UUID, byte[]> entry : md5Sums.entrySet()) {
                        sb.append("\n")
                        .append(Utils.md5ToString(entry.getValue()))
                        .append("  ")
                        .append(fileSessions.get(entry.getKey()).fileName());
                    }
                    sb.append("\n===\tEND Remote MD5 Sums\t=== \n");
                    logger.log(Level.INFO, sb.toString());
                }

            } else if (ctrlMsg.message instanceof String) {
                remoteDownMsg = (String)ctrlMsg.message;
                close(remoteDownMsg, null);
                logger.log(Level.WARNING, "\n\n [ FDTWriterSession ] Remote FDTReaderSession for session [ " + sessionID + " ] finished with errors:\n" + remoteDownMsg + "\n");
                if(!FdtMain.isIsServerMode())
                ShowMessageDialog.showErrorDialog("A server error occurred, please alert the server administrator and send the log file.", "Server Error");
            }
        } else {
            logger.log(Level.INFO, "[ FDTWriterSession ] Remote FDTReaderSession for session [ " + sessionID + " ] finished ok. Waiting for our side to finish.");
        }
        
    }

    private boolean doPreprocess() throws Exception {
        logger.log(Level.INFO, "[ FDTWriterSession ] Preprocessing started");

        int filtersCount = 0;
        final long sTime = System.nanoTime();

        try {
            final String preProcessFiltersProp = config.getPreFilters();

            ProcessorInfo processorInfo = new ProcessorInfo();

            if (preProcessFiltersProp == null || preProcessFiltersProp.length() == 0) {
                if (logger.isLoggable(Level.FINE)) {
                    logger.log(Level.FINE, "No FDT Preprocess Filters defined");
                }
            } else {
                final String[] preProcessFilters = preProcessFiltersProp.split(",");
                if (preProcessFilters == null || preProcessFilters.length == 0) {
                    logger.log(Level.WARNING, "Illegal -preFilters parameter");
                } else {
                    filtersCount = preProcessFilters.length;

                    processorInfo.fileList = new String[fileList.length];
                    processorInfo.destinationDir = (this.destinationDir == null) ? config.getDestinationDir()
                            : this.destinationDir;

                    System.arraycopy(fileList, 0, processorInfo.fileList, 0, fileList.length);

                    for (final String filterName: preProcessFilters) {
                        Preprocessor preprocessor = (Preprocessor) (Class.forName(filterName).newInstance());
                        preprocessor.preProcessFileList(processorInfo, this.controlChannel.subject);
                    }
                }
            }

            if (filtersCount > 0) {
                this.processorInfo = processorInfo;
            }
        } finally {
            StringBuffer sb = new StringBuffer();
            if(filtersCount > 0) {
                sb.append("[ FDTWriterSession ] Preprocessing: ").append(filtersCount).append(" filters in ").append(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - sTime)).append(" ms");
            } else {
                sb.append("[ FDTWriterSession ] No pre processing filters defined/processed.");
            }
            logger.log(Level.INFO, sb.toString());
        }

        return (filtersCount > 0);
    }

    private boolean doPostProcessing() throws Exception {

        if (!postProcessingDone.compareAndSet(false, true)) {
            return false;
        }

        int filtersCount = 0;
        final long sTime = System.nanoTime();

        logger.log(Level.INFO, "[ FDTWriterSession ] Post Processing started");

        try {
            ProcessorInfo processorInfo = (this.processorInfo == null) ? new ProcessorInfo() : this.processorInfo;

            final String postProcessFiltersProp = config.getPostFilters();

            // call the postProcessfilter
            if (postProcessFiltersProp == null || postProcessFiltersProp.length() == 0) {
                if (logger.isLoggable(Level.FINE)) {
                    logger.log(Level.INFO, " [ FDTWriterSession ] No FDT Postprocess Filters defined");
                }
            } else {
                String[] postProcessFilters = postProcessFiltersProp.split(",");
                if (postProcessFilters == null || postProcessFilters.length == 0) {
                    logger.log(Level.WARNING, "Cannot understand -postFilters");
                } else {
                    filtersCount = postProcessFilters.length;
                    processorInfo.fileList = new String[fileSessions.size()];
                    processorInfo.destinationDir = this.destinationDir;

                    System.arraycopy(fileList, 0, processorInfo.fileList, 0, fileList.length);

                    for (final String filterName: postProcessFilters) {
                        Postprocessor postprocessor = (Postprocessor) (Class.forName(filterName).newInstance());
                        postprocessor.postProcessFileList(processorInfo,
                                                          this.controlChannel.subject,
                                                          downCause(),
                                                          downMessage());
                    }
                }
            }
        } finally {
            StringBuffer sb = new StringBuffer();
            if(filtersCount > 0) {
                sb.append("[ FDTWriterSession ] Postprocessing: ").append(filtersCount).append(" filters in ").append(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - sTime)).append(" ms");
            } else {
                sb.append("[ FDTWriterSession ] No post processing filters defined/processed.");
            }
            logger.log(Level.INFO, sb.toString());
        }

        return (filtersCount > 0);
    }

    private void checkFinished(Throwable finishCause) {

        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER, "\n\n\n\n\n\n ---------------- [ FDTWriterSession ] finishedSessions.size(). " + finishedSessions.size() + " fileSessions.size() " + fileSessions.size());
        }

        if (finishedSessions.size() == fileSessions.size()) {

            if(!FdtMain.isIsServerMode())
            {
                GUIFileStatus guiFileStatus = GUIFileStatus.getGUIFileStatusInstance();
                guiFileStatus.existingFileUpdates();
            }

            if(downMessage() != null || downCause() != null) {
                close(downMessage(), downCause());
            } else {
                close(downMessage(), finishCause);
            }

            notifySessionFinished();

            Runnable finalR = new Runnable() {
                public void run() {
                    finalCleanup();
                }
            };

            Utils.getMonitoringExecService().schedule(finalR, 5, TimeUnit.SECONDS);
            
        }
    }

    @Override
    public void finishFileSession(UUID sessionID, Throwable finishCause) {
        super.finishFileSession(sessionID, finishCause);
        logger.log("Calling ceckFinished from finishFileSession");
        checkFinished(finishCause);
    }

    public boolean offer(final FileBlock fileBlock, long delay, TimeUnit unit) throws InterruptedException {
        if(isClosed()) {
            return false;
        }
        // TODO Auto-generated method stub
        FileSession fs = fileSessions.get(fileBlock.fileSessionID);
        if (fs != null) {
            return dwm.offerFileBlock(fileBlock, fs.partitionID(), delay, unit);
        }

        logger.log(Level.WARNING, "No such fileSession: " + fileBlock.fileSessionID + " in my session map");
        return false;
    }

    public void put(final FileBlock fileBlock) throws InterruptedException {
        if(isClosed()) {
            throw new InterruptedException("Session is closed");
        }
        
        FileSession fs = fileSessions.get(fileBlock.fileSessionID);
        if (fs != null) {
            dwm.putFileBlock(fileBlock, fs.partitionID());
        } else {
            logger.log(Level.SEVERE, "No such fileSession: " + fileBlock.fileSessionID + " in my session map");
        }
    }

}
