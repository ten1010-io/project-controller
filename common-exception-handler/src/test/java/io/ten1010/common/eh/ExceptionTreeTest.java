package io.ten1010.common.eh;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.ClosedFileSystemException;
import java.util.IllegalFormatException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExceptionTreeTest {

    @Test
    void getCompatibleException() {
        /*
         * Exception -- RuntimeException -- IllegalArgumentException
         *                               -- IllegalStateException
         *           -- IOException -- AccessDeniedException
         */
        ExceptionTree tree = new ExceptionTree();
        tree.addException(IllegalArgumentException.class);
        tree.addException(IOException.class);
        tree.addException(RuntimeException.class);
        tree.addException(AccessDeniedException.class);
        tree.addException(IllegalStateException.class);

        assertEquals(IllegalArgumentException.class, tree.getCompatibleException(IllegalFormatException.class));
        assertEquals(IllegalArgumentException.class, tree.getCompatibleException(IllegalArgumentException.class));
        assertEquals(IOException.class, tree.getCompatibleException(IOException.class));
        assertEquals(RuntimeException.class, tree.getCompatibleException(RuntimeException.class));
        assertEquals(RuntimeException.class, tree.getCompatibleException(UnsupportedOperationException.class));
        assertEquals(Exception.class, tree.getCompatibleException(Exception.class));
        assertEquals(IllegalStateException.class, tree.getCompatibleException(ClosedFileSystemException.class));

        tree.removeException(RuntimeException.class);

        assertEquals(Exception.class, tree.getCompatibleException(RuntimeException.class));
        assertEquals(Exception.class, tree.getCompatibleException(UnsupportedOperationException.class));
        assertEquals(IllegalArgumentException.class, tree.getCompatibleException(IllegalFormatException.class));
        assertEquals(IllegalArgumentException.class, tree.getCompatibleException(IllegalArgumentException.class));
        assertEquals(IllegalStateException.class, tree.getCompatibleException(ClosedFileSystemException.class));
    }

}