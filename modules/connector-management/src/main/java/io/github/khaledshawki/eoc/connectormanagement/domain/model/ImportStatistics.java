package io.github.khaledshawki.eoc.connectormanagement.domain.model;

/** Durable outcomes for source records whose enclosing page was accepted downstream. */
public record ImportStatistics(long fetched, long accepted, long rejected, long duplicates) {

  public static final ImportStatistics ZERO = new ImportStatistics(0, 0, 0, 0);

  public ImportStatistics {
    if (fetched < 0 || accepted < 0 || rejected < 0 || duplicates < 0) {
      throw new IllegalArgumentException("Import statistics cannot contain negative values");
    }

    long classified;
    try {
      classified = Math.addExact(Math.addExact(accepted, rejected), duplicates);
    } catch (ArithmeticException exception) {
      throw new IllegalArgumentException("Import statistics exceed the supported range", exception);
    }

    if (fetched != classified) {
      throw new IllegalArgumentException(
          "Fetched records must equal accepted, rejected, and duplicate records");
    }
  }

  public ImportStatistics plus(ImportStatistics increment) {
    if (increment == null) {
      throw new NullPointerException("Import statistics increment cannot be null");
    }

    try {
      return new ImportStatistics(
          Math.addExact(fetched, increment.fetched),
          Math.addExact(accepted, increment.accepted),
          Math.addExact(rejected, increment.rejected),
          Math.addExact(duplicates, increment.duplicates));
    } catch (ArithmeticException exception) {
      throw new IllegalStateException("Import statistics exceed the supported range", exception);
    }
  }
}
