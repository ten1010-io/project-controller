package io.ten1010.aipub.projectcontroller.controller;

import io.kubernetes.client.extended.controller.Controller;

public interface ControllerFactory {

    Controller createController();

}
