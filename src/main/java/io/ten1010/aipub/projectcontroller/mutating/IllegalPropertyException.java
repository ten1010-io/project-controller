package io.ten1010.aipub.projectcontroller.mutating;

import lombok.Getter;

@Getter
public class IllegalPropertyException extends IllegalArgumentException {

    public static IllegalPropertyException createNullError(String object, String propertyPath) {
        return new IllegalPropertyException(object, propertyPath, "Null not allowed");
    }

    private static final String MESSAGE_FORMAT = "IllegalProperty : object[%s], propertyPath[%s], propertyErrorMessage[%s]";

    private final String object;
    private final String propertyPath;
    private final String propertyErrorMessage;

    public IllegalPropertyException(String object, String propertyPath, String propertyErrorMessage) {
        super(String.format(MESSAGE_FORMAT, object, propertyPath, propertyErrorMessage));
        this.object = object;
        this.propertyPath = propertyPath;
        this.propertyErrorMessage = propertyErrorMessage;
    }

}
