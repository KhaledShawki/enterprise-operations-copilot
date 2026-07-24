package io.github.khaledshawki.eoc.platform.tenantaccess.adapter.out.persistence;

import java.util.Objects;
import org.hibernate.exception.ConstraintViolationException;

final class PersistenceConstraintViolationDetector {

  private PersistenceConstraintViolationDetector() {}

  static boolean hasConstraintName(Throwable exception, String constraintName) {

    Objects.requireNonNull(exception, "Exception cannot be null");
    Objects.requireNonNull(constraintName, "Constraint name cannot be null");

    Throwable current = exception;

    while (current != null) {
      if (current instanceof ConstraintViolationException constraintViolation
          && constraintName.equals(constraintViolation.getConstraintName())) {
        return true;
      }

      current = current.getCause();
    }

    return false;
  }
}
