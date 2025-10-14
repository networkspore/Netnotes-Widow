package io.netnotes.gui.fx.utils;

import java.io.Serializable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import io.netnotes.engine.messaging.NoteMessaging;
import io.netnotes.gui.fx.app.control.FrameRateMonitor;
import javafx.concurrent.Task;
import javafx.concurrent.WorkerStateEvent;
import javafx.event.EventHandler;

public class TaskUtils {
    private static ExecutorService virtualExecutor = null;
    private static boolean useAdaptiveDelay = true;

    /**
     * Enable or disable adaptive delay globally
     */
    public static void setAdaptiveDelay(boolean adaptive) {
        useAdaptiveDelay = adaptive;
    }
    
     /**
     * Schedule a delayed task with adaptive delay based on current frame rate.
     * Automatically uses FrameRateMonitor to determine optimal delay.
     */
    public static Future<?> fxDelay(EventHandler<WorkerStateEvent> onSucceeded) {
        long delay = useAdaptiveDelay 
            ? FrameRateMonitor.getInstance().getRecommendedDebounceDelay()
            : 16; // Default to ~60fps
        return fxDelay(delay, onSucceeded);
    }
    
    

    public static Future<?> returnException(String errorString, EventHandler<WorkerStateEvent> onFailed) {

        Task<Object> task = new Task<Object>() {
            @Override
            public Object call() throws Exception {
                throw new Exception(errorString);
            }
        };

        task.setOnFailed(onFailed);

        return getVirtualExecutor().submit(task);

    }

    public static Future<?> returnException(WorkerStateEvent stateEvent, EventHandler<WorkerStateEvent> onFailed){
        return returnException(stateEvent.getSource().getException(), onFailed);
    }

     public static Exception getCreateException( WorkerStateEvent event){
        Throwable throwable = event.getSource().getException();
        return getCreateException(throwable);
    }
    public static Exception getCreateException(Throwable throwable){
        if(throwable != null && throwable instanceof Exception){
            return (Exception) throwable;
        }else{
            return new Exception(throwable != null ? throwable.getMessage() : NoteMessaging.Error.UNKNOWN);
        }
    }

    public static Future<?> returnException(EventHandler<WorkerStateEvent> onFailed, WorkerStateEvent... stateEvents) {
        Exception combinedException = createCombinedException(stateEvents);
        return returnException(combinedException, onFailed);
    }

   
    public static Exception createCombinedException(WorkerStateEvent... stateEvents) {
        if (stateEvents == null || stateEvents.length == 0) {
            return new Exception(NoteMessaging.Error.UNKNOWN);
        }
        
        // Start with the first exception
        Exception rootException = getCreateException(stateEvents[0]);
        
        // Chain additional exceptions as suppressed exceptions
        if (stateEvents.length > 1) {
            for (int i = 1; i < stateEvents.length; i++) {
                Exception additionalException = getCreateException(stateEvents[i]);
                rootException.addSuppressed(additionalException);
            }
        }
        
        return rootException;
    }

    public static Future<?> returnException(Throwable throwable,EventHandler<WorkerStateEvent> onFailed){
        if(throwable != null && throwable instanceof Exception){
            return returnException((Exception) throwable, onFailed);
        }else{
            return returnException(throwable != null ? throwable.getMessage() : NoteMessaging.Error.UNKNOWN, onFailed);
        }
    }


    public static Future<?> returnException(Exception exception, EventHandler<WorkerStateEvent> onFailed) {

        Task<Object> task = new Task<Object>() {
            @Override
            public Object call() throws Exception {
                throw exception;
            }
        };

        task.setOnFailed(onFailed);

        return getVirtualExecutor().submit(task);

    }
    public static Object getSerializableSourceValue( WorkerStateEvent event){
        Object value = event.getSource().getValue();

        return value instanceof Serializable ? value : null;
    }



    public static String getErrorMsg(WorkerStateEvent failedEvent){
        return getErrorMsg(failedEvent.getSource().getException());
    }

    public static String getErrorMsg(Throwable throwable){
        return throwable != null ? throwable.toString() : NoteMessaging.Error.UNKNOWN;
    }
    

    public static ExecutorService getVirtualExecutor(){
        if(virtualExecutor == null){
            virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();
        }
        return virtualExecutor;
    }

    public static Future<?> fxDelay(long delayMillis,EventHandler<WorkerStateEvent> onSucceeded) {
        Task<Object> task = new Task<Object>() {
            @Override
            public Object call() throws InterruptedException {
                Thread.sleep(delayMillis);
                return null;
            }
        };
        task.setOnSucceeded(onSucceeded);
        return getVirtualExecutor().submit(task);
    }

  

    public static Future<?> returnObject(Object object, EventHandler<WorkerStateEvent> onSucceeded) {

        Task<Object> task = new Task<Object>() {
            @Override
            public Object call() {

                return object;
            }
        };
        task.setOnSucceeded(onSucceeded);
        return getVirtualExecutor().submit(task);
    }

}
