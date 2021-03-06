package net.hh.request_dispatcher;

import org.apache.log4j.Logger;
import org.zeromq.ZMQ;
import zmq.Proxy;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

/**
 * Dispatches work to multiple ZmqWorkers.
 *
 * Created by hartmann on 4/5/14.
 */
public class ZmqWorkerProxy {

    private static Logger log = Logger.getLogger(ZmqWorkerProxy.class);

    private static final String WORKER_PAYLOAD_CHANNEL = "inproc://workerPayload";
    private static final String WORKER_CONTROL_CHANNEL = "inproc://workerControl";

    private final Set<ZmqWorker> managedWorkers = new HashSet<ZmqWorker>();

    private final ZMQ.Context ctx;
    private final boolean isContextOwner;

    private final ZMQ.Socket outsideSocket;
    private final ZMQ.Socket payloadSocket;
    private final ZMQ.Socket controlSocket;

    private final Thread proxyLooper = new Thread(new Runnable() {
        @Override
        public void run() {
            doProxyLoop();
        }
    });

    // Internal state management
    private enum State {
        created,    // after constructor is called
        started,    // after startWorkers() was called
        stopped     // after shutdown() was called
    }
    private State state = State.created;

    /**
     * Creates a ZmqWorkerProxy object that listens for requests on the given input channel
     * and manages a set of ZmqWorker Threads.
     *
     * SIDE: Creates background thread with proxy loop.
     *
     * Call shutdown() to destroy threads, sockets and context properly.
     *
     * @param inputChannel    Endpoint to listen for requests.
     */
    public ZmqWorkerProxy(final String inputChannel) {
        this(inputChannel, ZMQ.context(1), true);
    }

    /**
     * Like ZmqWorkerProxy(String inputChannel) but uses provided context.
     *
     * Does not terminate context on shutdown().
     * ctx.term() need to be called manually.
     */
    public ZmqWorkerProxy(final ZMQ.Context ctx, final String inputChannel) {
        this(inputChannel, ctx, false);
    }


    private ZmqWorkerProxy(final String inputChannel, final ZMQ.Context ctx, final boolean isContextOwner) {
        this.ctx = ctx;
        this.isContextOwner = isContextOwner;

        outsideSocket = ctx.socket(ZMQ.ROUTER);
        outsideSocket.setLinger(100);
        outsideSocket.setHWM(1000);
        outsideSocket.bind(inputChannel);

        payloadSocket = ctx.socket(ZMQ.DEALER);
        payloadSocket.setLinger(100);
        payloadSocket.setHWM(1000);
        payloadSocket.bind(WORKER_PAYLOAD_CHANNEL);

        controlSocket = ctx.socket(ZMQ.PUB);
        controlSocket.setLinger(100);
        controlSocket.setHWM(1000);
        controlSocket.bind(WORKER_CONTROL_CHANNEL);

        proxyLooper.start();
    }

    /**
     * Start all currently maintained workers.
     *
     * Can only be called once.
     */
    public void startWorkers() {
        if (state != State.created) {
            throw new IllegalStateException("Workers already started.");
        }

        for(ZmqWorker worker : managedWorkers) {
            worker.start();
        }

        state = State.started;
    }

    /**
     * Stop all worker threads.
     *
     * Can only be called when threads startWorkers() has been called.
     *
     * If context was passed to constructor, the context is kept alive.
     * As a consquence the proxy loop keeps running until ctx.term() is
     * called by another thread.
     */
    public void shutdown() {
        try {
            if (state != State.started) {
                throw new IllegalStateException("Workers not started.");
            }

            // Avoids concurrent modification exception in zmq libarary with when Loging is enabled.
            Thread.sleep(100);

            // terminate worker threads
            controlSocket.send(ZmqWorker.Commands.CMD_STOP);
            for (ZmqWorker worker : managedWorkers) {
                worker.join();
            }
            controlSocket.close();

            if (isContextOwner) {
                // terminates proxy loop and closes outsideSocket and payloadSocket.
                ctx.term();
            }

            state = State.stopped;

        } catch (InterruptedException e) {
            log.error("Interrupted join", e);
            throw new IllegalStateException(e);
        }
    }

    //////////////////////  SET INTERFACE IMPLEMENTATION ////////////////////////////

    /**
     * Add a ZmqWorker for maintenance by ZmqWorkerProxy.
     *
     * @param worker to be added
     * @return true if this set did not already contain the specified worker
     */
    public boolean add(final ZmqWorker worker) {
        worker.replaceWorkSocket(generateWorkerSocket());
        worker.replaceControlSocket(generateControlSocket());
        return managedWorkers.add(worker);
    }

    /**
     * Remove worker object from maintenance by ZmqWorkerProxy.
     *
     * @param worker to be removed
     * @return true if this set contained the specified element
     */
    public boolean remove(final ZmqWorker worker) {
        return managedWorkers.remove(worker);
    }

    /**
     * Clear all workers from maintenance.
     */
    public void clear() {
        managedWorkers.clear();
    }

    ////////////////////////////// HELPER METHODS /////////////////////////////////

    private ZMQ.Socket generateWorkerSocket() {
        ZMQ.Socket socket = ctx.socket(ZMQ.ROUTER);
        socket.setLinger(100);
        socket.setHWM(1);
        socket.connect(WORKER_PAYLOAD_CHANNEL);
        return socket;
    }

    private ZMQ.Socket generateControlSocket() {
        ZMQ.Socket socket = ctx.socket(ZMQ.SUB);
        socket.subscribe(new byte[0]);
        socket.setLinger(100);
        socket.setHWM(10);
        socket.connect(WORKER_CONTROL_CHANNEL);
        return socket;
    }

    /**
     * Start serving workers at port.
     *
     * Proxy.proxy() returns when when ctx.term() is called.
     */
    private void doProxyLoop() {
        Proxy.proxy(outsideSocket.base(), payloadSocket.base(), null);
        log.info("Terminated proxy");
        outsideSocket.close();
        payloadSocket.close();
    }

    //// Convenience methods

    /**
     * Adds multiple workers that handle request to proxy object.
     *
     * @param numWorkers     number of concurrent workers
     * @param handler       handles request
     */
    public <S extends Serializable,T extends Serializable>  void add(int numWorkers, RequestHandler<S,T> handler) {
        for (int i=0; i<numWorkers; i++) {
            add(new ZmqWorker<S,T>(handler));
        }
    }
}
