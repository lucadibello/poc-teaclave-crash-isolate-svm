package ch.usi.inf.crashme;

import ch.usi.inf.crashme.common.api.SimpleService;
import org.apache.teaclave.javasdk.host.Enclave;
import org.apache.teaclave.javasdk.host.EnclaveFactory;
import org.apache.teaclave.javasdk.host.EnclaveType;
import org.apache.teaclave.javasdk.host.exception.EnclaveCreatingException;
import org.apache.teaclave.javasdk.host.exception.EnclaveDestroyingException;
import org.apache.teaclave.javasdk.host.exception.ServicesLoadingException;

import java.util.Iterator;

public class CrashMe {
    private static Enclave currentEnclave;
    private static final int ROUNDS = 5;

    public static SimpleService loadTestService() {
        // Create enclave for SimpleService
        // NOTE: the enclave is started in this thread!
        System.out.println("Starting enclave in thread: " + Thread.currentThread().getId());
        try {
            CrashMe.currentEnclave = EnclaveFactory.create(EnclaveType.TEE_SDK);
        } catch (EnclaveCreatingException e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to create enclave: " + e.getMessage());
        }
        System.out.println("Enclave started in thread: " + Thread.currentThread().getId());

        // Load the service into the enclave
        System.out.println("Loading SimpleService in thread: " + Thread.currentThread().getId());
        Iterator<SimpleService> iter = null;
        try {
            iter = currentEnclave.load(SimpleService.class);
        } catch (ServicesLoadingException e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to load SimpleService: " + e.getMessage());
        }
        if (!iter.hasNext()) {
            throw new RuntimeException("No SimpleService implementation found");
        }
        SimpleService simpleService = iter.next();
        System.out.println("SimpleService loaded in thread: " + Thread.currentThread().getId());
        return simpleService;
    }

    /**
     * Test 1 - Create and use an enclave in the same thread. Destroy in main thread
     */
    public static void test1() throws EnclaveDestroyingException {
        // Load the test service
        SimpleService simpleService = loadTestService();

        // Make a couple of calls from this thread
        System.out.println("Making calls to SimpleService in thread: " + Thread.currentThread().getId());
        for (int i = 0; i < 5; i++) {
            String message = "Hello " + i;
            String response = simpleService.echo(message);
            System.out.println("Response from enclave in thread " + Thread.currentThread().getName() + ": " + response);
        }
        System.out.println("Done");

        // Destroy the enclave
        CrashMe.currentEnclave.destroy();
    }

    /**
     * Test 2 - Using an enclave started in one thread from another thread. Destroy in main thread
     */
    public static void test2() throws EnclaveDestroyingException, InterruptedException {
        // Load enclave in main thread
        SimpleService simpleService = loadTestService();

        // This thread is not able to use the service!
        Thread thread = new Thread(() -> {
            for (int i = 0; i < CrashMe.ROUNDS; i++) {
                try {
                    String message = "Hello from thread " + i;
                    String response = simpleService.echo(message);
                    System.out.println("Response from enclave in thread " + Thread.currentThread().getId() + ": " + response);
                } catch (Exception e ) {
                   System.out.println("Exception in thread " + Thread.currentThread().getId() + ": " + e.getMessage() + " " + e);
                }
            }
        });

        // Start the thread
        thread.start();
        thread.join();

        // Destroy the enclave in the main thread after starting the new thread
        CrashMe.currentEnclave.destroy();
    }

    /**
     * Test 3 - Create enclave in one thread, use in another thread. Destroy in same thread as use.
     */
    public static void test3() throws InterruptedException {

        Thread thread = new Thread(() -> {
            // Load the test service
            SimpleService simpleService = loadTestService();

            // Make a couple of calls from this thread
            System.out.println("Making calls to SimpleService in thread: " + Thread.currentThread().getId());
            for (int i = 0; i < CrashMe.ROUNDS; i++) {
                String message = "Hello from thread " + i;
                String response = simpleService.echo(message);
                System.out.println("Response from enclave in thread " + Thread.currentThread().getId() + ": " + response);
            }
            System.out.println("Done");

            // Destroy the enclave
            try {
                CrashMe.currentEnclave.destroy();
            } catch (EnclaveDestroyingException e) {
                System.out.println("Exception while destroying enclave in thread " + Thread.currentThread().getId() + ": " + e.getMessage());
                throw new RuntimeException(e);
            }
        });

        thread.start();
        thread.join();
    }


    /**
     * Create enclave in one thread, use it in the same thread, but destroy it in main thread.
     */
    public static void test4() throws EnclaveDestroyingException, InterruptedException {
        Thread thread = new Thread(() -> {
            // Load the test service
            SimpleService simpleService = loadTestService();

            // Make a couple of calls from this thread
            System.out.println("Making calls to SimpleService in thread: " + Thread.currentThread().getId());
            for (int i = 0; i < CrashMe.ROUNDS; i++) {
                String message = "Hello from thread " + i;
                String response = simpleService.echo(message);
                System.out.println("Response from enclave in thread " + Thread.currentThread().getId() + ": " + response);
            }
            System.out.println("Done");
        });

        thread.start();
        thread.join();

        // Use static reference to destroy the enclave in main thread
        CrashMe.currentEnclave.destroy();
    }

    public static void main(String[] args) throws EnclaveDestroyingException, InterruptedException {
        System.out.println();
        System.out.println("Running test 1: Create and use enclave in same thread, destroy in main thread");
        test1();

        System.out.println();
        System.out.println("------------------------------------------------------------------------------");
        System.out.println();

        System.out.println("\nRunning test 2: Create enclave in main thread, use in another thread, destroy in main thread");
        test2();

        System.out.println();
        System.out.println("------------------------------------------------------------------------------");
        System.out.println();

        System.out.println("\nRunning test 3: Create enclave in one thread, use and destroy in same thread");
        test3();

        System.out.println();
        System.out.println("------------------------------------------------------------------------------");
        System.out.println();

        // NOTE: until this point, everything should work.

        System.out.println("\nRunning test 4: Create and use enclave in one thread, destroy in main thread");
        test4(); // When calling destroy from the thread, it crashes!
    }
}
