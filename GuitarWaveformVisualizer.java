package guitar;
import javax.sound.sampled.*;
import javax.swing.*;
import java.awt.*;
import java.util.Arrays;

public class GuitarWaveformVisualizer {

    static final int WIDTH = 1600;
    static final int HEIGHT = 800;
    static final int BUFFER_SIZE = 1024;

    public static void main(String[] args) throws Exception {
        int mixerIndex = 12; // Adjust if needed

        Mixer.Info[] mixerInfos = AudioSystem.getMixerInfo();
        if (mixerIndex >= mixerInfos.length) {
            System.err.println("Invalid mixer index!");
            return;
        }

        Mixer mixer = AudioSystem.getMixer(mixerInfos[mixerIndex]);
        AudioFormat format = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                48000.0f,
                16,
                1,
                2, // 2 bytes per frame
                48000.0f,
                true // big-endian
        );

        DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
        if (!AudioSystem.isLineSupported(info)) {
            System.err.println("Line not supported for this format.");
            return;
        }

        TargetDataLine line = (TargetDataLine) mixer.getLine(info);
        line.open(format);
        line.start();

        JFrame frame = new JFrame("Guitar Waveform");
        WaveformPanel panel = new WaveformPanel();
        frame.add(panel);
        frame.setSize(WIDTH, HEIGHT);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);

        new Thread(() -> {
            byte[] buffer = new byte[BUFFER_SIZE];
            while (true) {
                int bytesRead = line.read(buffer, 0, buffer.length);
                short[] samples = new short[bytesRead / 2];
                for (int i = 0; i < samples.length; i++) {
                    int high = buffer[2 * i];
                    int low = buffer[2 * i + 1];
                    samples[i] = (short) ((high << 8) | (low & 0xFF));
                }
                panel.updateWaveform(samples);
            }
        }).start();
    }

    static class WaveformPanel extends JPanel {
        private short[] samples = new short[BUFFER_SIZE / 2];

        public void updateWaveform(short[] newSamples) {
            this.samples = Arrays.copyOf(newSamples, newSamples.length);
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            g.setColor(Color.BLACK);
            g.fillRect(0, 0, getWidth(), getHeight());
            g.setColor(Color.GREEN);

            int midY = getHeight() / 2;
            double xScale = getWidth() / (double) samples.length;

            for (int i = 0; i < samples.length - 1; i++) {
                int x1 = (int) (i * xScale);
                int x2 = (int) ((i + 1) * xScale);
                int y1 = midY - samples[i] * getHeight() / 65536;
                int y2 = midY - samples[i + 1] * getHeight() / 65536;
                g.drawLine(x1, y1, x2, y2);
            }
        }
    }
}
