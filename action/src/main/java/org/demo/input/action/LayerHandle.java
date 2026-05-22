package org.demo.input.action;


public interface LayerHandle extends AutoCloseable {

    @Override
    void close();

    boolean isActive();
}