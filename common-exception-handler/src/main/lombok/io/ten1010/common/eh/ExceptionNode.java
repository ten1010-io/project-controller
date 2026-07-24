package io.ten1010.common.eh;

import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

@Getter
public class ExceptionNode {

    private final Class<? extends Exception> exClass;
    private final List<ExceptionNode> children;

    public ExceptionNode(Class<? extends Exception> exClass) {
        this.exClass = exClass;
        this.children = new ArrayList<>();
    }

    public void addChild(ExceptionNode child) {
        this.children.add(child);
    }

    public void removeChild(ExceptionNode child) {
        this.children.remove(child);
    }

}
