package ch.usi.inf.crashme.common.api;

import org.apache.teaclave.javasdk.common.annotations.EnclaveService;

@EnclaveService
public interface SimpleService {
    String echo(String msg);
}
