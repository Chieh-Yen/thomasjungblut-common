package de.jungblut.partition;

/**
 * This partitioner partitions connected ranges from 0 to numberOfRows into
 * sizeOfCluster buckets.
 * 
 * @author thomas.jungblut
 * 
 */
public class BlockPartitioner implements Partitioner {

  @Override
  public Boundaries partition(int sizeOfCluster, int numberOfRows) {
    if (sizeOfCluster == 0)
      throw new IllegalStateException("Size of Cluster should not be 0!");
    Boundaries bounds = new Boundaries();

    int avg = Math.round(numberOfRows / sizeOfCluster);
    int neededSplits = sizeOfCluster;
    int leftPieces;
    int start = 0;
    while (neededSplits > 0) {
      bounds.addRange(start, start += (avg - 1));
      start++;
      leftPieces = numberOfRows - start;
      neededSplits--;
      if (neededSplits > 0) {
        avg = leftPieces / neededSplits;
      }
    }

    return bounds;
  }
}
