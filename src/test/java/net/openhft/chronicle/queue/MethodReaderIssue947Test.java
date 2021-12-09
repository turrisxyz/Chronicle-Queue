package net.openhft.chronicle.queue;

import net.openhft.chronicle.bytes.MethodReader;
import net.openhft.chronicle.queue.impl.single.SingleChronicleQueue;
import net.openhft.chronicle.queue.impl.single.SingleChronicleQueueBuilder;
import net.openhft.chronicle.wire.Marshallable;
import net.openhft.chronicle.wire.WireType;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicReference;

public class MethodReaderIssue947Test extends QueueTestCommon {

    public static class Payload implements Marshallable {
        private String text;
        private Integer number;

        public Payload(String text, int number) {
            this.text = text;
            this.number = number;
        }
    }

    public interface Interface<P extends Marshallable> {
        void process(P payload);
    }

    public static abstract class Abstract<P extends Marshallable> implements Interface<P> {
    }

    public static class Implementation extends Abstract<Payload> {
        private static final AtomicReference<Payload> receiver = new AtomicReference<>();

        public void process(final Payload payload) {
            receiver.compareAndSet(null, payload);
        }
    }

    @Test
    public void test() throws IOException {
        try (SingleChronicleQueue cq = SingleChronicleQueueBuilder
                .builder(Files.createTempDirectory(null), WireType.BINARY_LIGHT)
                .build()) {
            Payload payload = new Payload("text", 1);
            Interface writer = cq.methodWriter(Interface.class);
            Implementation implementation = new Implementation();
            MethodReader reader = cq.createTailer().methodReader(implementation);
            Assert.assertTrue("check that generated class compiled", !Proxy.isProxyClass(reader.getClass()));
            writer.process(payload);
            reader.readOne();
            Assert.assertNotNull(Implementation.receiver.get());
        }
    }
}
