package io.ten1010.common.eh;

public class ExceptionTree {
    private final ExceptionNode root = new ExceptionNode(Exception.class);

    public void addException(Class<? extends Exception> exClass) {
        if (exClass.equals(Exception.class)) {
            throw new IllegalArgumentException("Cannot add an Exception.class");
        }
        this.addChild(exClass, this.root);
    }

    public void removeException(Class<? extends Exception> exClass) {
        if (exClass.equals(Exception.class)) {
            throw new IllegalArgumentException("Cannot remove an Exception.class");
        }
        this.removeChild(exClass, this.root);
    }

    public Class<? extends Exception> getCompatibleException(Class<? extends Exception> exClass) {
        return this.getChildNodeOrSelf(exClass, this.root).getExClass();
    }

    private void addChild(Class<? extends Exception> exClass, ExceptionNode node) {
        boolean added = false;
        for (ExceptionNode child : node.getChildren()) {
            if (child.getExClass().equals(exClass)) {
                throw new IllegalArgumentException("Already contains child class " + exClass);
            }
            if (!child.getExClass().isAssignableFrom(exClass)) continue;
            this.addChild(exClass, child);
            added = true;
        }
        if (!added) {
            ExceptionNode newNode = new ExceptionNode(exClass);
            for (ExceptionNode child : node.getChildren()) {
                if (!newNode.getExClass().isAssignableFrom(child.getExClass())) continue;
                node.removeChild(child);
                newNode.addChild(child);
            }
            node.addChild(newNode);
        }
    }

    private void removeChild(Class<? extends Exception> exClass, ExceptionNode node) {
        for (ExceptionNode child : node.getChildren()) {
            if (!child.getExClass().isAssignableFrom(exClass)) continue;
            if (child.getExClass().equals(exClass)) {
                node.removeChild(child);
                child.getChildren().forEach(node::addChild);
                break;
            }
            this.removeChild(exClass, child);
        }
    }

    private ExceptionNode getChildNodeOrSelf(Class<? extends Exception> exClass, ExceptionNode node) {
        for (ExceptionNode child : node.getChildren()) {
            if (!child.getExClass().isAssignableFrom(exClass)) continue;
            if (child.getExClass().equals(exClass)) {
                return child;
            }
            return this.getChildNodeOrSelf(exClass, child);
        }
        return node;
    }
}
