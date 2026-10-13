package guitar;
import javax.sound.sampled.*;

public class CaptureAudio {
    public static void main(String[] args) throws Exception {
    	Mixer.Info[] mixers = AudioSystem.getMixerInfo();
    	for (Mixer.Info mixerInfo : mixers) {
    	    System.out.println("Mixer: " + mixerInfo.getName());
    	}
        // Define the audio format you want (e.g., 48 kHz, 16-bit, mono)
        AudioFormat format = new AudioFormat(48000, 16, 1, true, false);

        // Get a TargetDataLine for the USB audio device
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);

        if (!AudioSystem.isLineSupported(info)) {
            System.out.println("Line not supported");
            System.exit(0);
        }

        TargetDataLine line = (TargetDataLine) AudioSystem.getLine(info);
        line.open(format);
        line.start();

        byte[] buffer = new byte[4096];
        System.out.println("Start capturing audio...");

        while (true) {
            int bytesRead = line.read(buffer, 0, buffer.length);
            for (int i = 0; i < bytesRead; i++) {
                //System.out.print(String.format("%02X ", buffer[i]));  // Hex output
            }
            //System.out.println();
            // Now buffer contains raw PCM audio data
            // You can process it or save it
            //System.out.println("Read " + bytesRead + " bytes");
        }
    }
}
