package io.ten1010.aipub.projectcontroller.domain.k8s;

public final class NodeMaintenanceConstants {

    public static final String NN_DRAIN = "drain";
    public static final String NN_CORDON = "cordon";
    public static final String NN_UNCORDON = "uncordon";
    public static final String NN_PROGRESS = "PROGRESS";
    public static final String NN_COMPLETED = "COMPLETED";
    public static final String NN_DAEMONSET = "DaemonSet";

    private NodeMaintenanceConstants() {
    }

}
