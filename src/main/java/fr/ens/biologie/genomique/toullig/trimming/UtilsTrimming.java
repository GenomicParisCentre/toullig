package fr.ens.biologie.genomique.toullig.trimming;

import java.io.IOException;

import fr.ens.biologie.genomique.eoulsan.bio.Sequence;
import fr.ens.biologie.genomique.eoulsan.bio.io.FastaWriter;

import static fr.ens.biologie.genomique.eoulsan.EoulsanLogger.getLogger;

/**
 * Class of somes utils methods for cutadaptTrimming. Created by birer on
 * 29/03/17.
 * @author Aurelien Birer
 */
public class UtilsTrimming {

  /**
   * Method of the class TrimModes to obtain the sequence of the left outlier.
   * @param lengthOutlierBegin, the length of the outlier
   * @param sequence, the sequence of the read
   * @return the sequence of the left outlier
   */
  public static String getOutlierLeftSequence(int lengthOutlierBegin,
      String sequence) {

    // test if the sequence is null
    if (sequence == null) {

      getLogger().info("the sequence use is null !");
      System.exit(0);
      return null;
    }

    if (lengthOutlierBegin >= 0) {

      return sequence.substring(0, lengthOutlierBegin);

    } else {

      return "";

    }

  }

  /**
   * Method of the class TrimModes to obtain the sequence of the right outlier.
   * @param lengthOutlierRight, the length of the outlier
   * @param sequence, the sequence of the read
   * @return the sequence of the right outlier
   */
  public static String getOutlierRightSequence(int lengthOutlierRight,
      String sequence) {

    // test if the sequence is null
    if (sequence == null) {

      getLogger().info("the sequence use is null !");
      System.exit(0);
      return null;
    }

    if (lengthOutlierRight >= 0) {

      return sequence.substring(sequence.length() - lengthOutlierRight,
          sequence.length());

    } else {

      return "";

    }

  }

  /**
   * Method of the class TrimModes to obtain the score of the left outlier.
   * @param lengthOutlierBegin, the length of the outlier
   * @param score, the score of the read
   * @return , a string of quality
   */
  public static String getOutlierLeftQuality(int lengthOutlierBegin,
      String score) {

    // test if the score is null
    if (score == null) {

      getLogger().info("the score use is null !");
      System.exit(0);
      return null;
    }

    return score.substring(0, lengthOutlierBegin);

  }

  /**
   * Method of the class TrimModes to obtain the score of the right outlier.
   * @param lengthOutlierEnd, the length of the outlier
   * @param score, the score of the read
   * @return , a string of quality
   */
  public static String getOutlierRightQuality(int lengthOutlierEnd,
      String score) {

    // test if the score is null
    if (score == null) {

      getLogger().info("the score use is null !");
      System.exit(0);
      return null;
    }

    return score.substring(score.length() - lengthOutlierEnd, score.length());

  }

  //
  // get Outlier
  //

  /**
   * Method of the class TrimModes to write the outliers (3' and 5') in fasta
   * files.
   * @param leftLengthOutlier, the length of the outlier right
   * @param rightLengthOutlier, the length of the outlier left
   * @param sequence, the sequence of the read
   * @param id, the id of the read
   */
  public static void writeOutliers(int leftLengthOutlier,
      int rightLengthOutlier, String sequence, String id,
      FastaWriter leftFastaWriter, FastaWriter rightFastaWriter) {

    // test if the sequence is null
    if (sequence == null) {

      getLogger().info("the sequence use is null !");
      System.exit(0);
    }

    // test if the id is null
    if (id == null) {

      getLogger().info("the id use is null !");
      System.exit(0);
    }

    // get the left outlier
    String leftOutlierSequence =
        getOutlierLeftSequence(leftLengthOutlier, sequence);

    // get the right outlier
    String rightOutlierSequence =
        getOutlierRightSequence(rightLengthOutlier, sequence);

    try {

      Sequence leftFasta = new Sequence();

      // get id and sequence of the left outlier
      leftFasta.setName(id);
      leftFasta.setSequence(leftOutlierSequence);

      // write the left outlier
      leftFastaWriter.write(leftFasta);

      Sequence rightFasta = new Sequence();

      // get id and sequence of the right outlier
      rightFasta.setName(id);
      rightFasta.setSequence(rightOutlierSequence);

      // write the right outlier
      rightFastaWriter.write(rightFasta);

    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}
