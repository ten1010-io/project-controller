package io.ten1010.aipub.projectcontroller.informer;

import io.kubernetes.client.informer.SharedInformerFactory;

public interface InformerRegistrar {

    void registerInformer(SharedInformerFactory informerFactory);

}
