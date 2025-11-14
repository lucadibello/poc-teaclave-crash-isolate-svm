package ch.usi.inf.crashme.enclave;

import ch.usi.inf.crashme.common.api.SimpleService;
import com.google.auto.service.AutoService;

@AutoService(SimpleService.class)
public class SimpleEnclaveImpl implements SimpleService {
    @Override
    public String echo(String msg) {
        // Reverse the input string
        return new StringBuilder(msg).reverse().toString();
    }
}
