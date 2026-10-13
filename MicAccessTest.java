package guitar;
import javax.sound.sampled.*;

public class MicAccessTest {
    public static void main(String[] args) {
        AudioFormat format = new AudioFormat(48000.0f, 16, 1, true, false);
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);

        try {
            TargetDataLine line = (TargetDataLine) AudioSystem.getLine(info);
            line.open(format);
            line.start();
            System.out.println("Microphone line opened successfully.");
            line.stop();
            line.close();
        } catch (LineUnavailableException e) {
            System.err.println("Microphone not available or access denied.");
            e.printStackTrace();
        }
    }
}