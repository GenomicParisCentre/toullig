package fr.ens.biologie.genomique.toullig;

import com.google.common.primitives.Ints;
import fr.ens.biologie.genomique.eoulsan.bio.Alphabet;
import fr.ens.biologie.genomique.eoulsan.bio.ReadSequence;
import fr.ens.biologie.genomique.eoulsan.bio.io.FastqReader;
import fr.ens.biologie.genomique.eoulsan.util.LocalReporter;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SamInputResource;
import htsjdk.samtools.SamReader;
import htsjdk.samtools.SamReaderFactory;
import org.usadellab.trimmomatic.fastq.FastqRecord;
import org.usadellab.trimmomatic.trim.IlluminaClippingTrimmer;
import org.usadellab.trimmomatic.trim.Trimmer;
import org.usadellab.trimmomatic.util.Logger;


import java.io.*;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static fr.ens.biologie.genomique.eoulsan.bio.Alphabets.*;
import static fr.ens.biologie.genomique.eoulsan.bio.Sequence.reverseComplement;

/**
 * Created by birer on 24/02/17.
 */
public class TrimFastq {

    private HashMap<String, String[]> fastqHash = new HashMap<String, String[]>();
    private String adaptorRT;
    private String adaptorStrandSwitching;
    private String pathOutputTrimLeftFasta;
    private String pathOutputTrimRightFasta;

    private File samFile;
    private File fastqFile;
    private File workDir;
    private File nameOutputFastq;
    private File adaptorFile;


    private boolean processPTrim;
    private boolean processSWTrim;
    private boolean processCutadapt;
    private boolean processTrimmomatic;
    private boolean processStats;

    private int minLenProcess;
    private int lengthWindowsSW;
    private double thresholdSW;

    private double errorRateCutadapt;
    private int seedMismatchesTrimmomatic;
    private int palindromeClipThresholdTrimmomatic;
    private int simpleClipThreshold;

    private BufferedWriter fastaFileLeftOutlier;
    private BufferedWriter fastaFileRightOutlier;
    private Alphabet alphabet = AMBIGUOUS_DNA_ALPHABET;

    public TrimFastq(File samFile, File fastqFile, File adaptorFile, File nameOutputFastq, File workDir) throws IOException, InterruptedException {

        if (!samFile.exists()) {
            throw new IOException(
                    "The file " + samFile + " dont exist!");
        } else {
            this.samFile = samFile;
        }

        if (!fastqFile.exists()) {
            throw new IOException(
                    "The file " + fastqFile + " dont exist!");
        } else {
            this.fastqFile = fastqFile;
        }

        if (!nameOutputFastq.exists()) {
            throw new IOException(
                    "The file " + nameOutputFastq + " dont exist!");
        } else {
            this.nameOutputFastq = nameOutputFastq;
        }

        if (!adaptorFile.exists()) {
            throw new IOException(
                    "The file " + adaptorFile + " dont exist!");
        } else {
            this.adaptorFile = adaptorFile;
        }

        if (!workDir.exists()) {
            throw new IOException(
                    "The directory " + workDir + " dont exist!");
        } else {
            this.workDir = workDir;
        }
    }

    /**
     * Method of the class TrimFastq to trim sequence to create sequences files for cutadapt.
     * @throws IOException
     * @throws InterruptedException
     */
    private void trimOutlierWithPMethod() throws IOException, InterruptedException {

        int count1=0;
        int count2=0;
        int count3=0;
        int count4=0;
        int count5=0;
        int count6=0;
        int count7=0;
        int count8=0;


        BufferedWriter BufferedWriterFastqOutput = new BufferedWriter(new FileWriter(this.nameOutputFastq));

        for (String ID : this.fastqHash.keySet()) {

            int lengthOutlierEnd=0;
            int lengthOutlierBegin=0;
            count1++;
            String[] tabValue = this.fastqHash.get(ID);
            String sequence = tabValue[0];
            String score = tabValue[1];
            //trim by CIGAR
            if(!tabValue[2].equals("*")){

                //Repere Soft and Hard clipping
                int beginIndexExtremite=tabValue[2].indexOf("S");
                if(beginIndexExtremite>tabValue[2].indexOf("H") && tabValue[2].indexOf("H")>=0){
                    beginIndexExtremite=tabValue[2].indexOf("H");
                }

                if(beginIndexExtremite!=-1 && beginIndexExtremite+1!=tabValue[2].length() && beginIndexExtremite<=6){
                    tabValue[3]=tabValue[2].substring(0,beginIndexExtremite);
                    lengthOutlierBegin = Integer.parseInt(tabValue[2].substring(0,beginIndexExtremite));

                    count2++;
                }else{
                    //System.out.println("CIGAR problem deb: "+tabValue[2]);
                    lengthOutlierBegin=0;
                }

                //Repere Soft and Hard clipping
                int endIndexExtremite=tabValue[2].lastIndexOf("S");
                if(endIndexExtremite<tabValue[2].lastIndexOf("H")){
                    endIndexExtremite=tabValue[2].lastIndexOf("H");
                }
                if(endIndexExtremite==-1){
                    endIndexExtremite=0;
                }

                if(endIndexExtremite+1==tabValue[2].length() && tabValue[2].length()-endIndexExtremite<5 ){
                    tabValue[4]=tabValue[2].substring(tabValue[2].lastIndexOf("M")+1,endIndexExtremite);
                    lengthOutlierEnd = Integer.parseInt(tabValue[2].substring(tabValue[2].lastIndexOf("M")+1,endIndexExtremite));
                    count3++;
                }else{
                    //System.out.println("CIGAR problem end: "+tabValue[2]);
                    lengthOutlierEnd=0;
                }

                if(this.processCutadapt){
                    writeOutlier(lengthOutlierBegin,lengthOutlierEnd,sequence,ID);
                }

                if(this.processTrimmomatic){


                    String leftOutlierSequence= getOutlierLeftSequence(lengthOutlierBegin,sequence);
                    String rightOutlierSequence= getOutlierRightSequence(lengthOutlierEnd,sequence);
                    String leftOutlierScore= getOutlierLeftScore(lengthOutlierBegin,score);
                    String rightOutlierScore= getOutlierRightScore(lengthOutlierEnd,score);

                    String mainSequence = sequence.substring(lengthOutlierBegin,sequence.length()-lengthOutlierEnd);
                    String mainScore = score.substring(lengthOutlierBegin,score.length()-lengthOutlierEnd);



                    String rigthTrimSequence=TrimmomaticTrim(rightOutlierSequence,rightOutlierScore);
                    String leftTrimSequence=TrimmomaticTrim(reverse(leftOutlierSequence),reverse(leftOutlierScore));

                    String rigthTrimScore=score.substring(mainScore.length(),mainScore.length()+rightOutlierSequence.length());
                    String leftTrimScore=score.substring(leftOutlierScore.length()-leftTrimSequence.length(),leftOutlierScore.length());

                    String sequenceTranscript=leftTrimSequence+mainSequence+rigthTrimSequence;
                    String scoreTranscript = leftTrimScore+mainScore+rigthTrimScore;

                    if(sequenceTranscript.length()>=this.minLenProcess){
                        writeFastq( ID, sequenceTranscript, scoreTranscript, BufferedWriterFastqOutput);
                    }else{
                        count8++;
                    }
                }
            }else{
                count4++;
            }
            if(tabValue[5].equals("16")){
                count5++;
            }
            if(tabValue[5].equals("0")){
                count6++;
            }
            if(tabValue[5].equals("4")){
                count7++;
            }
        }
        System.out.println("Nombre total de sequence dans le SAM : "+count1);
        System.out.println("Nombre de sequence CIGAR '*': "+count4);
        System.out.println("Nombre de QFlag '16': "+count5);
        System.out.println("Nombre de QFlag '0': "+count6);
        System.out.println("Nombre de QFlag '4': "+count7);
        System.out.println("Nombre d'Outlier Debut bien trouvé: "+count2);
        System.out.println("Nombre d'Outlier Fin bien trouvé: "+count3);
        if(this.processTrimmomatic) {
            System.out.println("Nombre de sequence trop petite (<" + this.minLenProcess + ") : " + count8);
            BufferedWriterFastqOutput.close();
        }
    }

    /**
     * Method of the class TrimFastq to trim sequence to create sequences files for cutadapt with the side-windows method.
     * @throws IOException
     * @throws InterruptedException
     */
    private void trimOutlierWithSWMethod() throws IOException, InterruptedException {

        Pattern pattern1 = Pattern.compile("(([0-9]*[A-Z]).*)");
        Pattern pattern2 = Pattern.compile("([0-9]*)(.)");
        int longueurSequenceCigar=0;

        for (String ID : this.fastqHash.keySet()) {

            String sequenceCigarBinaire="";
            String[] tabValue = this.fastqHash.get(ID);
            String sequence = tabValue[0];
            String CIGAR=tabValue[2];

            if(!CIGAR.equals("*")){

                while(CIGAR.length()!=0){

                    Matcher matcher1 = pattern1.matcher(CIGAR);
                    boolean b = matcher1.matches();
                    // si recherche fructueuse
                    if(b) {

                        // pour chaque groupe
                        String oneCigare=matcher1.group(2);
                        CIGAR=CIGAR.substring(matcher1.group(2).length(),CIGAR.length());
                        Matcher matcher2 = pattern2.matcher(oneCigare);
                        boolean b2 = matcher2.matches();
                        // si recherche fructueuse
                        if(b2){

                            longueurSequenceCigar=Integer.parseInt(matcher2.group(1))-1;
                            for(int i=0;i<=longueurSequenceCigar;i++){

                                if(matcher2.group(2).equals("M")){
                                    sequenceCigarBinaire=sequenceCigarBinaire+1;
                                    continue;
                                }
                                if(matcher2.group(2).equals("N") || matcher2.group(2).equals("D")){
                                    continue;
                                }
                                if(!matcher2.group(2).equals("N") || !matcher2.group(2).equals("M")){
                                    sequenceCigarBinaire=sequenceCigarBinaire+0;
                                }
                            }
                        }
                    }
                }

                int lengthOutlierBegin=sideWindowsLeft(sequenceCigarBinaire);
                int lengthOutlierEnd=sideWindowsRight(sequenceCigarBinaire);
                tabValue[3]=""+lengthOutlierBegin;
                tabValue[4]=""+lengthOutlierEnd;
                writeOutlier(lengthOutlierBegin,lengthOutlierEnd,sequence,ID);
            }
        }
    }

    //
    //Merge outlier
    //

    /**
     * Method of the class TrimFastq to merge the results of cutadapt with the trim sequence.
     * @throws IOException
     */
    private void mergeTrimOutlier() throws IOException {

        BufferedWriter fastqTrimBufferedWritter= new BufferedWriter(new FileWriter(this.nameOutputFastq));
        HashMap<String, String> fastaLeftHash = new HashMap<String, String>();
        HashMap<String, String> fastaRightHash = new HashMap<String, String>();
        String shortestFastqSequence="";
        int i = 0;
        int countNotWriteFastq=0;

        //Read of the left outlier fasta output by cutadapt
        try{
            BufferedReader outputTrimLeftFastaReader = new BufferedReader(new FileReader(this.pathOutputTrimLeftFasta));
            String line = "";
            while ((line = outputTrimLeftFastaReader.readLine()) != null) {
                if(line.contains(">")){
                    String[] part=line.split(">");
                    fastaLeftHash.put(part[1],outputTrimLeftFastaReader.readLine());
                }
            }
        }catch (IOException e){
            e.printStackTrace();
        }

        //Read of the right outlier fasta output by cutadapt
        try{
            BufferedReader outputTrimRightFastaFile = new BufferedReader(new FileReader(this.pathOutputTrimRightFasta));
            String line = "";
            while ((line = outputTrimRightFastaFile.readLine()) != null) {
                if(line.contains(">")){
                    String[] part=line.split(">");
                    fastaRightHash.put(part[1],outputTrimRightFastaFile.readLine());
                }
            }
        }catch (IOException e){
            e.printStackTrace();
        }

        System.out.println("Start of merging sequence !");

        //merge of the outlier trim on the main sequence
        for (String ID : this.fastqHash.keySet()) {

            i++;
            String leftSequence="";
            String rightSequence="";
            int lengthBeginOutlier=0;
            int lengthEndOutlier=0;
            String[] tabValue = this.fastqHash.get(ID);

            if(!tabValue[2].equals("*")) {

                if (tabValue[3].equals("")) {
                    lengthBeginOutlier = 0;
                } else {
                    lengthBeginOutlier = Integer.parseInt(tabValue[3]);
                }
                if (tabValue[4].equals("")) {
                    lengthEndOutlier = 0;
                } else {
                    lengthEndOutlier = Integer.parseInt(tabValue[4]);
                }

                String mainSequenceWithoutOutlier = tabValue[0].substring(lengthBeginOutlier, tabValue[0].length() - lengthEndOutlier);

                if (fastaLeftHash.get(ID) == null) {
                    leftSequence = "";
                } else {
                    leftSequence = fastaLeftHash.get(ID);
                }
                if (fastaLeftHash.get(ID) == null) {
                    rightSequence = "";
                } else {
                    rightSequence = fastaRightHash.get(ID);
                }

                String scoreTrim = tabValue[1].substring(lengthBeginOutlier - leftSequence.length(), (tabValue[1].length() - lengthEndOutlier) + rightSequence.length());
                String sequenceTrim = leftSequence + mainSequenceWithoutOutlier + rightSequence;

                if (sequenceTrim.length() >= this.minLenProcess) {
                    writeFastq(ID, sequenceTrim, scoreTrim, fastqTrimBufferedWritter);
                } else {
                    countNotWriteFastq++;
                }

                if (i == 1) {
                    shortestFastqSequence = sequenceTrim;
                }
                if (shortestFastqSequence.length() >= sequenceTrim.length()) {
                    shortestFastqSequence = sequenceTrim;
                }
            }
        }
        System.out.println("Nombre de sequence trop petite (<"+this.minLenProcess+") : "+countNotWriteFastq);
        System.out.println("la séquence trim la plus courte fait : " + shortestFastqSequence.length());
        fastqTrimBufferedWritter.close();
    }

    //
    //Cutadapt
    //

    /**
     * Method of the class TrimFastq to execute cutadapt.
     * @param pathFastaFileOutlier, the path of the fasta file with outlier to trim
     * @param strand, the cutadapt strand of the adaptor
     * @param infoTrimPath, path to the output log of cutadapt
     * @throws IOException
     * @throws InterruptedException
     */
    private void cutadaptTrim(String pathFastaFileOutlier, String strand, String infoTrimPath) throws IOException, InterruptedException {

        try {
            String reverseComplementAdaptorRT=strand+" reverse_complement_RT_adaptor="+reverseComplement(this.adaptorRT,this.alphabet);
            String reverseComplementAdaptorSwithStrand=strand+" reverse_complement_Switch_Strand_RT_adaptor="+reverseComplement(this.adaptorStrandSwitching,this.alphabet);

            String complementAdaptorRT=strand+" complement_RT_adaptor="+complement(this.adaptorRT,this.alphabet);
            String complementAdaptorSwithStrand=strand+" complement_Switch_Strand_RT_adaptor="+complement(this.adaptorStrandSwitching,this.alphabet);

            StringBuffer reverseAdaptorRTStringBuffer = new StringBuffer(this.adaptorRT);
            StringBuffer reverseAdaptorSwithStrandStringBuffer = new StringBuffer(this.adaptorStrandSwitching);
            String reverseAdaptorRT= strand+" reverse_RT_adaptor="+reverseAdaptorRTStringBuffer.reverse().toString();
            String reverseAdaptorSwithStrand= strand+" reverse_Switch_Strand_RT_adaptor="+reverseAdaptorSwithStrandStringBuffer.reverse().toString();

            this.adaptorRT=strand+" RT_adaptor="+this.adaptorRT;
            this.adaptorStrandSwitching=strand+" Switch_Strand_RT_adaptor="+this.adaptorStrandSwitching;

            ProcessBuilder pb = new ProcessBuilder("/bin/bash", "-c", "cutadapt "+this.adaptorRT+" "+this.adaptorStrandSwitching+" "+reverseAdaptorRT+" "+reverseAdaptorSwithStrand+" "+complementAdaptorRT+" "+complementAdaptorSwithStrand+" "+reverseComplementAdaptorRT+" "+reverseComplementAdaptorSwithStrand+""+" --quiet --error-rate="+this.errorRateCutadapt+" --info-file="+infoTrimPath+" --overlap=7 --times=8 --match-read-wildcards --format=fasta "+pathFastaFileOutlier+" > "+this.pathOutputTrimLeftFasta);

            //System.out.println(pb.command());
            pb.redirectErrorStream(true);
            Process proc = pb.start(); // Start the process.

            //getLogCutadapt(proc);

            int exitproc=proc.waitFor();
//            if(!proc.waitFor(1, TimeUnit.MINUTES)) {
//                //timeout - kill the process.
//                proc.destroy(); // consider using destroyForcibly instead
//            }
        }
        catch (IOException e) {
            e.printStackTrace(); // or log it, or otherwise handle it
        }
        catch (InterruptedException ie) {
            ie.printStackTrace(); // or log it, or otherwise handle it
        }
    }

    /**
     * Method of the class TrimFastq to display log of cutadapt (delete the option --quiet).
     * @param proc, a Processus
     * @throws IOException
     */
    private void getLogCutadapt(Process proc) throws IOException {

        BufferedReader stdInput = new BufferedReader(new
                InputStreamReader(proc.getInputStream()));
        BufferedReader stdError = new BufferedReader(new
                InputStreamReader(proc.getErrorStream()));

        // read the output from the command
        System.out.println("Here is the standard output of the command:\n");
        String s = null;
        while ((s = stdInput.readLine()) != null) {
            System.out.println(s);
        }
        stdInput.close();

        // read any errors from the attempted command
        System.out.println("Here is the standard error of the command (if any):\n");
        while ((s = stdError.readLine()) != null) {
            System.out.println(s);
        }
        stdError.close();
    }


    //
    // Trimmomatic
    //

    private String TrimmomaticTrim(String sequence, String score) throws IOException {

        System.out.println(this.adaptorFile.getPath()+":"+this.seedMismatchesTrimmomatic+":"+this.palindromeClipThresholdTrimmomatic+":"+this.simpleClipThreshold);
        Logger logger = new Logger(true,true,true);
        Trimmer trimer = IlluminaClippingTrimmer.makeIlluminaClippingTrimmer(logger, this.adaptorFile.getPath()+":"+this.seedMismatchesTrimmomatic+":"+this.palindromeClipThresholdTrimmomatic+":"+this.simpleClipThreshold);
        FastqRecord record = new FastqRecord("name", sequence, "", score, 33);
        FastqRecord [] result = trimer.processRecords(new FastqRecord[] {record});
        return result[0].getSequence();
    }

    //
    // get Outlier
    //

    /**
     * Method of the class TrimFastq to write the outliers (3' and 5') in fasta files.
     * @param lengthOutlierBegin, the length of the outlier right
     * @param lengthOutlierEnd, the length of the outlier left
     * @param sequence, the sequence of the read
     * @param ID, the ID of the read
     * @throws IOException
     */
    private void writeOutlier(int lengthOutlierBegin, int lengthOutlierEnd, String sequence, String ID) throws IOException {

        String leftOutlierSequence= getOutlierLeftSequence(lengthOutlierBegin,sequence);
        String rightOutlierSequence= getOutlierRightSequence(lengthOutlierEnd,sequence);
        writeFasta(leftOutlierSequence,ID,this.fastaFileLeftOutlier);
        writeFasta(rightOutlierSequence,ID,this.fastaFileRightOutlier);
    }

    /**
     * Method of the class TrimFastq to obtain the sequence of the left outlier.
     * @param lengthOutlierBegin, the length of the outlier
     * @param sequence, the sequence of the read
     * @return the sequence of the left outlier
     */
    private String getOutlierLeftSequence(int lengthOutlierBegin, String sequence){

        return sequence.substring(0,lengthOutlierBegin);
    }

    /**
     * Method of the class TrimFastq to obtain the sequence of the right outlier.
     * @param lengthOutlierEnd, the length of the outlier
     * @param sequence, the sequence of the read
     * @return the sequence of the right outlier
     */
    private String getOutlierRightSequence(int lengthOutlierEnd, String sequence){

        return sequence.substring(sequence.length()-lengthOutlierEnd,sequence.length());
    }

    /**
     * Method of the class TrimFastq to obtain the score of the left outlier.
     * @param lengthOutlierBegin, the length of the outlier
     * @param score, the score of the read
     * @return
     */
    private String getOutlierLeftScore(int lengthOutlierBegin, String score){
        return score.substring(0,lengthOutlierBegin);
    }

    /**
     * Method of the class TrimFastq to obtain the score of the right outlier.
     * @param lengthOutlierEnd, the length of the outlier
     * @param score, the score of the read
     * @return
     */
    private String getOutlierRightScore(int lengthOutlierEnd, String score){
        return score.substring(score.length()-lengthOutlierEnd,score.length());
    }

    //
    // get index by side window
    //

    /**
     * Method of the class TrimFastq to the length of the left outlier with a binair CIGAR sequence.
     * @param sequenceCigarBinaire, a binaire CIGAR sequence
     * @return int, the length of the outlier
     */
    private int sideWindowsLeft(String sequenceCigarBinaire){

        if(sequenceCigarBinaire==null){
            return 0;
        }
        String windows="";
        int length=0;
        for(int i = 0;i<=sequenceCigarBinaire.length();i++){
            if(i==(sequenceCigarBinaire.length()-this.lengthWindowsSW-2)){
                break;
            }
            windows=sequenceCigarBinaire.substring(i,i+this.lengthWindowsSW);
            if(sumWindowCIGAR(windows)>=this.thresholdSW){
                length=i+this.lengthWindowsSW;
                break;
            }
        }
        return length;
    }

    /**
     * Method of the class TrimFastq to the length of the right outlier with a binair CIGAR sequence.
     * @param sequenceCigarBinaire, a binaire CIGAR sequence
     * @return int, the length of the outlier
     */
    private int sideWindowsRight(String sequenceCigarBinaire){

        if(sequenceCigarBinaire==null){
            return 0;
        }
        String windows="";
        int length=0;
        for(int i = sequenceCigarBinaire.length();i>=0;i--){
            if(i==this.lengthWindowsSW){
                break;
            }

            windows=sequenceCigarBinaire.substring(i-this.lengthWindowsSW,i);
            if(sumWindowCIGAR(windows)>=this.thresholdSW){
                if(i>=sequenceCigarBinaire.length()-this.lengthWindowsSW){
                    length=sequenceCigarBinaire.length()-i;
                }else{
                    length=sequenceCigarBinaire.length()-i-this.lengthWindowsSW;
                }
                break;
            }
        }
        return length;
    }


    /**
     * Method of the class TrimFastq to sumWindowCIGAR a sequence CIGAR encode in 1/0.
     * @param windows, a String of CIGAR sequence encode in 1/0
     * @return double, the sumWindowCIGAR of the CIGAR window
     */
    private double sumWindowCIGAR(String windows){
        String[] arrayWindows = windows.split("");
        int sum=0;
        for(int i = 0;i<=arrayWindows.length-1;i++){
            if(arrayWindows[i].equals("1")){
                sum+=1;
            }
        }
        return (double)sum/windows.length();
    }

    //
    // Write
    //

    /**
     * Method of the class TrimFastq to write a sequence to the fasta format.
     * @param sequence, the sequence of the read
     * @param ID, the ID of the read
     * @param fastaFile, the file to write fasta sequence
     * @throws IOException
     */
    private void writeFasta(String sequence, String ID, BufferedWriter fastaFile) throws IOException {

        try {
            fastaFile.write(">"+ID+"\n");
            for (int i = 0; i <= sequence.length(); i = i + 60) {
                if (i + 60 >= sequence.length()) {
                    fastaFile.write(sequence.substring(i, sequence.length()) + "\n");
                    break;
                }
                fastaFile.write(sequence.substring(i, i + 60) + "\n");
            }
        }catch (IOException e){
            e.printStackTrace();
        }
    }

    /**
     * Method of the class TrimFastq to write a sequence to the fastq format.
     * @param ID, the ID of the read
     * @param sequence, the sequence of the read
     * @param score, the score of the read
     * @param fastqTrimBufferedWritter, the file to write fastq sequence
     * @throws IOException
     */
    private void writeFastq(String ID,String sequence,String score, BufferedWriter fastqTrimBufferedWritter)throws IOException{

        fastqTrimBufferedWritter.write("@"+ID+"\n");
        fastqTrimBufferedWritter.write(sequence+"\n");
        fastqTrimBufferedWritter.write("+\n");
        fastqTrimBufferedWritter.write(score+"\n");
    }

    //
    // Read Files
    //

    /**
     * Method of the class TrimFastq to read and get the adaptor in the adaptor file.
     * @param adaptorFile, the file who contains adaptor
     * @throws IOException
     */
    private void readAdaptorRTFile(BufferedReader adaptorFile)throws IOException{

        int i =0;
        String line;
        while ((line = adaptorFile.readLine()) != null) {
            if(i==1){
                this.adaptorRT =line;
            }
            if(i==3){
                this.adaptorStrandSwitching =line;
            }
            i++;
        }
        adaptorFile.close();
    }

    /**
     * Method of the class TrimFastq to read and get the ID,sequence, score and CIGAR of a sam file.
     * @param samInputStream, the input stream file sam
     * @throws IOException
     */
    private void readSamFile(InputStream samInputStream) throws IOException {

        try(final SamReader inputSam =
                    SamReaderFactory.makeDefault().open(SamInputResource.of(samInputStream))){

            String lengthBeginOutlier="";
            String lengthEndOutlier="";
            String score="";
            String sequence="";

            HashMap<String, String> fastqHashMultimapped = new HashMap<String, String>();

            for (SAMRecord samRecord : inputSam) {

                String CIGAR=samRecord.getCigarString();

                String QFlag = ""+samRecord.getFlags();
                String ID =samRecord.getReadName();
                int cigarLength=samRecord.getCigarLength();


                if(this.fastqHash.containsKey(ID)) {
                    fastqHashMultimapped.put(ID,"");
                    String[] tabvalue= this.fastqHash.get(ID);
                    if(Integer.parseInt(tabvalue[6])<=cigarLength){
                        this.fastqHash.put(ID, new String[]{sequence, score, CIGAR, lengthBeginOutlier, lengthEndOutlier, QFlag, ""+cigarLength});
                    }else{
                        continue;
                    }

                }else {
                    this.fastqHash.put(ID, new String[]{sequence, score, CIGAR, lengthBeginOutlier, lengthEndOutlier, QFlag, ""+cigarLength});
                }
            }
            System.out.println("Nombre de sequence qui ont fait ont mappé plusieurs fois : "+fastqHashMultimapped.size());
            inputSam.close();
        }catch (IOException e){
            e.printStackTrace();
        }
    }


    /**
     * Method of the class TrimFastq to read a fastq file.
     * @param fastqBufferedReader, a BufferedReader fastq file
     * @throws IOException
     */
    private void readFastqFile(File fastqBufferedReader)throws IOException{

        try (FastqReader reader = new FastqReader(fastqBufferedReader)) {
            for (ReadSequence read : reader) {

                String header = read.getName();
                String[] part=header.split(" ");
                String ID = part[0];
                String sequence = read.getSequence();
                String score = read.getQuality();
                String[] tabValue = this.fastqHash.get(ID);
                tabValue[0]=sequence;
                tabValue[1]=score;
            }
            reader.close();
        }catch (IOException e){
            e.printStackTrace();
        }
    }


    //
    //  utils
    //


    /**
     * Method of the class TrimFastq to make some stats of the trimming
     * @param infoTrimPath, a path to store stats in a file
     * @throws IOException
     */
    private void statsLogCutadapt(String infoTrimPath) throws IOException {

        LocalReporter localReporter = new LocalReporter();
        BufferedReader infoTrimLeftFile = new BufferedReader(new FileReader(infoTrimPath));
        String line = "";

        while ((line = infoTrimLeftFile.readLine()) != null) {

            String[] part=line.split("\t");
            String ID = part[0];
            String error = part[1];

            if(error.equals("-1")){
                localReporter.incrCounter(ID, "no_adaptor_found", 1);
                continue;
            }

            String nameAdaptor=part[7];

            if(localReporter.getCounterNames(ID).size()>1){
                if(localReporter.getCounterNames(ID).contains(nameAdaptor)){
                    long value=localReporter.getCounterValue(ID, nameAdaptor);
                    localReporter.incrCounter(ID,nameAdaptor,value+1);
                }else{
                    localReporter.setCounter(ID,nameAdaptor,1);
                }
            }else{
                localReporter.setCounter(ID,nameAdaptor,1);
            }
        }

        int count0=0;
        HashMap <String,Integer> hashStats = new HashMap<>();

        for ( String ID : localReporter.getCounterGroups()){

            if(localReporter.getCounterNames(ID).contains("no_adaptor_found")){
                count0++;
                hashStats.put("no_adaptor_found",count0);
            }else{
                for(int i=0;i<=8;i++){
                    if(localReporter.getCounterNames(ID).size()==i){
                        for(String adaptor : localReporter.getCounterNames(ID)){
                            if(hashStats.containsKey(adaptor+"_found_"+i+"_time")){
                                int oldValue=hashStats.get(adaptor+"_found_"+i+"_time");
                                hashStats.put(adaptor+"_found_"+i+"_time", Ints.checkedCast(localReporter.getCounterValue(ID,adaptor))+oldValue);
                            }else{
                                hashStats.put(adaptor+"_found_"+i+"_time", Ints.checkedCast(localReporter.getCounterValue(ID,adaptor)));
                            }
                        }
                    }
                }
            }
        }
        System.out.println(hashStats);
    }


    /**
     * Method of the class TrimFastq to reverse sequence.
     * @param sequence, a sequence
     * @return a string of a reversed sequence
     */
    private static final String reverse(final String sequence) {

        if (sequence == null) {
            return null;
        }

        final char[] array = sequence.toCharArray();
        final int len = array.length;
        final StringBuilder sb = new StringBuilder(len);

        for (int i = len - 1; i >= 0; i--) {
            sb.append(array[i]);
        }
        return sb.toString();
    }

    /**
     * Get the sequence as the complement. This method work only with
     * A,T,G and C bases.
     * @param sequence sequence to reverse complement
     * @param alphabet alphabet of the sequence to reverse complement
     * @return the reverse complement sequence
     */
    private static final String complement(final String sequence,
                                           final Alphabet alphabet) {

        if (sequence == null || alphabet == null) {
            return null;
        }

        String s =
                reverseComplement(sequence,alphabet);
        final char[] array = s.toCharArray();
        final int len = array.length;
        final StringBuilder sb = new StringBuilder(len);

        for (int i = len-1; i >=  0; i--) {
            sb.append(array[i]);
        }
        return sb.toString();
    }

    //
    //  Set
    //

    /**
     * Method of the class TrimFastq to set the process P in trim mode.
     * @param processPTrim, a boolean
     */
    public void setProcessPTrim(boolean processPTrim) {
        this.processPTrim = processPTrim;
    }

    /**
     * Method of the class TrimFastq to set the process SW in trim mode.
     * @param processSWTrim, a boolean
     */
    public void setProcessSWTrim(boolean processSWTrim) {
        this.processSWTrim = processSWTrim;
    }

    /**
     * Method of the class TrimFastq to set the process cutadapt.
     * @param processCutadapt, a boolean
     */
    public void setProcessCutadapt(boolean processCutadapt) {
        this.processCutadapt = processCutadapt;
    }

    /**
     * Method of the class TrimFastq to set the process trimmomatic.
     * @param processTrimmomatic, a boolean
     */
    public void setProcessTrimmomatic(boolean processTrimmomatic) {
        this.processTrimmomatic = processTrimmomatic;
    }

    /**
     * Method of the class TrimFastq to set the process stats.
     * @param processStats, a boolean
     */
    public void setProcessStats(boolean processStats) {
        this.processStats = processStats;
    }

    public void setMinimunLengthToWrite(int minlen){
        this.minLenProcess = minlen;
    }

    public void setLengthWindowsSW(int lengthWindowsSW){
        this.lengthWindowsSW=lengthWindowsSW;
    }

    public void setThresholdSW(double thresholdSW){
        this.thresholdSW=thresholdSW;
    }

    public void setErrorRateCutadapt(double errorRateCutadapt){
        this.errorRateCutadapt=errorRateCutadapt;
    }

    public void setSeedMismatchesTrimmomatic(int seedMismatchesTrimmomatic){
        this.seedMismatchesTrimmomatic=seedMismatchesTrimmomatic;
    }

    public void setPalindromeClipThresholdTrimmomatic(int palindromeClipThresholdTrimmomatic){
        this.palindromeClipThresholdTrimmomatic=palindromeClipThresholdTrimmomatic;
    }

    public void setSimpleClipThreshold(int simpleClipThreshold){
        this.simpleClipThreshold=simpleClipThreshold;
    }

    //
    // Execution
    //

    /**
     * Method of the class TrimFastq to execute the process with cutadapt.
     * @param fastqFile, a fastq file
     * @throws IOException
     * @throws InterruptedException
     */
    private void executionTrimWithCutadapt(File fastqFile) throws IOException, InterruptedException {

        String pathFastaFileLeftOutlier = this.workDir+"/fastaFileLeftOutlier.fasta";
        String pathFastaFileRightOutlier = this.workDir+"/fastaFileRightOutlier.fasta";

        String infoTrimLeftPath = this.workDir+"/logCutadaptLeftOutlier.txt";
        String infoTrimRightPath = this.workDir+"/logCutadaptRightOutlier.txt";

        readFastqFile(fastqFile);

        this.fastaFileLeftOutlier= new BufferedWriter(new FileWriter(pathFastaFileLeftOutlier));
        this.fastaFileRightOutlier= new BufferedWriter(new FileWriter(pathFastaFileRightOutlier));

        System.out.println("Trim begin !");

        if(this.processPTrim){
            trimOutlierWithPMethod();
        }
        if(this.processSWTrim){
            trimOutlierWithSWMethod();
        }

        this.fastaFileRightOutlier.close();
        this.fastaFileLeftOutlier.close();

        System.out.println("Begin use cutadapt !");

        String strandLeft="-g";
        String strandRight="-a";

        // Cutadapt execution
        cutadaptTrim(pathFastaFileLeftOutlier, strandLeft, infoTrimLeftPath);
        cutadaptTrim(pathFastaFileRightOutlier, strandRight, infoTrimRightPath);

        // Merge the output form cutadapt
        mergeTrimOutlier();

        if(this.processStats){
            statsLogCutadapt(infoTrimLeftPath);
            statsLogCutadapt(infoTrimRightPath);
        }
    }

    /**
     * Method of the class TrimFastq to execute the process with trimmomatic.
     * @param fastqFile, a fastq file
     * @throws IOException
     * @throws InterruptedException
     */
    private void executionTrimWithTrimmomatic(File fastqFile) throws IOException, InterruptedException {

        readFastqFile(fastqFile);

        System.out.println("Begin use trimmomatic !");

        if(this.processPTrim){
            trimOutlierWithPMethod();
        }
        if(this.processSWTrim){
            trimOutlierWithSWMethod();
        }
    }

    //
    // Main execution
    //

    /**
     * Method of the class TrimFastq to execute the trimming.
     * @throws IOException
     * @throws InterruptedException
     */
    public void execution() throws IOException, InterruptedException {

        this.pathOutputTrimLeftFasta= this.workDir+"/outputFastaFileLeftOutlier.fastq";
        this.pathOutputTrimRightFasta= this.workDir+"/outputFastaFileRightOutlier.fastq";

        //Problem with ONT skip read for the RT adaptor (to many TTTT..)
        readAdaptorRTFile(new BufferedReader(new FileReader(this.adaptorFile)));

        InputStream samInputStream =new FileInputStream(this.samFile);
        readSamFile(samInputStream);

        if(this.processCutadapt){
            executionTrimWithCutadapt(this.fastqFile);
        }
        if(this.processTrimmomatic){
            executionTrimWithTrimmomatic(this.fastqFile);
        }
    }
}

