package io.netnotes.gui.fx.utils;

import java.io.Serializable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import io.netnotes.engine.messaging.NoteMessaging;
import javafx.concurrent.Task;
import javafx.concurrent.WorkerStateEvent;
import javafx.event.EventHandler;

public class FxTaskHelpers {
    
    public static Future<?> returnException(String errorString, ExecutorService execService, EventHandler<WorkerStateEvent> onFailed) {

        Task<Object> task = new Task<Object>() {
            @Override
            public Object call() throws Exception {
                throw new Exception(errorString);
            }
        };

        task.setOnFailed(onFailed);

        return execService.submit(task);

    }

    public static Future<?> returnException(WorkerStateEvent stateEvent, ExecutorService execService, EventHandler<WorkerStateEvent> onFailed){
        return returnException(stateEvent.getSource().getException(), execService, onFailed);
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

    public static Future<?> returnException(ExecutorService execService, EventHandler<WorkerStateEvent> onFailed, WorkerStateEvent... stateEvents) {
        Exception combinedException = createCombinedException(stateEvents);
        return returnException(combinedException, execService, onFailed);
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

    public static Future<?> returnException(Throwable throwable, ExecutorService execService, EventHandler<WorkerStateEvent> onFailed){
        if(throwable != null && throwable instanceof Exception){
            return returnException((Exception) throwable, execService, onFailed);
        }else{
            return returnException(throwable != null ? throwable.getMessage() : NoteMessaging.Error.UNKNOWN, execService, onFailed);
        }
    }

    public static Future<?> returnException(Exception exception, ExecutorService execService, EventHandler<WorkerStateEvent> onFailed) {

        Task<Object> task = new Task<Object>() {
            @Override
            public Object call() throws Exception {
                throw exception;
            }
        };

        task.setOnFailed(onFailed);

        return execService.submit(task);

    }
    public static Object getSerializableSourceValue( WorkerStateEvent event){
        Object value = event.getSource().getValue();

        return value instanceof Serializable ? value : null;
    }


    public static Future<?> returnObject(Object object, ExecutorService execService, EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed) {

        Task<Object> task = new Task<Object>() {
            @Override
            public Object call() {

                return object;
            }
        };

        task.setOnFailed(onFailed);

        task.setOnSucceeded(onSucceeded);

        return execService.submit(task);

    }

    
    public static String getErrorMsg(WorkerStateEvent failedEvent){
        return getErrorMsg(failedEvent.getSource().getException());
    }

    public static String getErrorMsg(Throwable throwable){
        return throwable != null ? throwable.toString() : NoteMessaging.Error.UNKNOWN;
    }
    

    public static Future<?> delayObject(Object object, long delayMillis, ExecutorService execService, EventHandler<WorkerStateEvent> onSucceeded) {

        Task<Object> task = new Task<Object>() {
            @Override
            public Object call() throws InterruptedException {
                Thread.sleep(delayMillis);
                return object;
            }
        };

        task.setOnSucceeded(onSucceeded);

        return execService.submit(task);

    }


    public static Future<?> returnObject(Object object, ExecutorService execService, EventHandler<WorkerStateEvent> onSucceeded) {

        Task<Object> task = new Task<Object>() {
            @Override
            public Object call() {

                return object;
            }
        };

        task.setOnSucceeded(onSucceeded);

        return execService.submit(task);

    }

}
