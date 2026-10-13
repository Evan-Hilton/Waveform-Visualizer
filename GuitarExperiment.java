package guitar;

import javax.sound.sampled.*;
import javax.swing.*;

import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Arrays;

public class GuitarExperiment extends JPanel {
	public static int width = 1024;
	public static int height = 800;
	public static int frameRate = 10; // ms per frame
	public static int currentFrame = 0;
	public static Color backgroundColor = Color.white;

	// Rolling buffer that holds the waveform to display
	private static final int BUFFER_SIZE = 8192;
	private static short[] currentSamples = new short[BUFFER_SIZE];
	private static boolean spectral = true;
	public static double[] fftMagnitudes = new double[256];
	public static final int SAMPLE_RATE = 48000;

	public static int waveformHeight = height / 2;
	public static int spectrumHeight = height / 2;

	public static boolean synthesize = false;
	public static Thread synthThread = null;

	public static double noiseThreshold = 0.001;

	private static TargetDataLine line;

	public static void initialize() {
		// Start audio capture in a separate thread
		new Thread(() -> {
			try {
				AudioFormat format = new AudioFormat(SAMPLE_RATE, 16, 1, true, true);
				DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
				TargetDataLine line = (TargetDataLine) AudioSystem.getLine(info);
				line.open(format);
				line.start();

				byte[] buffer = new byte[currentSamples.length * 2];
				while (true) {
					line.read(buffer, 0, buffer.length);
					synchronized (currentSamples) {
						for (int i = 0; i < currentSamples.length; i++) {
							int hi = buffer[2 * i];
							int lo = buffer[2 * i + 1];
							currentSamples[i] = (short) ((hi << 8) | (lo & 0xff));
						}
						fftMagnitudes = calculateFFTAmplitudes(currentSamples);
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}).start();
	}

	public static void mainLoop() {
		// no logic for now
	}

	public static void paintLoop(Graphics2D g) {
		g.setColor(Color.black);

		// Draw waveform
		synchronized (currentSamples) {
			int midY = waveformHeight / 2;
			for (int i = 1; i < currentSamples.length; i++) {
				int x1 = (i - 1) * width / currentSamples.length;
				int y1 = midY - (currentSamples[i - 1] / 256);
				int x2 = i * width / currentSamples.length;
				int y2 = midY - (currentSamples[i] / 256);
				g.drawLine(x1, y1, x2, y2);
			}

			// Draw frequency spectrum (log scale)
			g.setColor(Color.black);
			double minFreq = 80; // E2
			double maxFreq = 2000; // Up to around 6th harmonic on high E string

			int numBins = fftMagnitudes.length;
			for (int i = 1; i < numBins; i++) {
				double freq = i * SAMPLE_RATE / (2.0 * numBins);
				if (freq < minFreq || freq > maxFreq)
					continue;

				// Logarithmic scaling of frequency
				double logMin = Math.log10(minFreq);
				double logMax = Math.log10(maxFreq);
				double logFreq = Math.log10(freq);
				double normX = (logFreq - logMin) / (logMax - logMin);

				int x = (int) (normX * width);
				int barHeight = (int) (fftMagnitudes[i] * 300); // scale for visibility
				int y = waveformHeight + spectrumHeight - barHeight;
				g.fillRect(x, height - barHeight, 3, barHeight);
				//g.drawLine(x, y, x, waveformHeight + spectrumHeight);
			}
		}
	}

	// Converts short samples buffer to amplitudes array (frequency domain)
	// input length must be power of two
	public static double[] calculateFFTAmplitudes(short[] samples) {
		int n = samples.length;
		if (Integer.bitCount(n) != 1) {
			throw new IllegalArgumentException("Input length must be power of two");
		}

		// Convert shorts to doubles (real parts), imag parts zero
		double[] real = new double[n];
		double[] imag = new double[n];
		for (int i = 0; i < n; i++) {
			real[i] = samples[i];
			imag[i] = 0;
		}

		// Optional: apply Hanning window to reduce spectral leakage
		for (int i = 0; i < n; i++) {
			real[i] *= 0.5 * (1 - Math.cos(2 * Math.PI * i / (n - 1)));
		}

		// Run FFT
		fft(real, imag);

		// Compute magnitudes (only first n/2 due to symmetry)
		double[] magnitudes = new double[n / 2];
		for (int i = 0; i < n / 2; i++) {
			magnitudes[i] = Math.sqrt(real[i] * real[i] + imag[i] * imag[i]);
		}

		// Normalize
		double max = Arrays.stream(magnitudes).max().orElse(1);
		for (int i = 0; i < magnitudes.length; i++) {
			magnitudes[i] /= max;
		}

		return magnitudes;
	}

	// Compute the FFT of given real input samples (imag parts assumed zero)
	// real and imag arrays must be the same length, a power of two
	public static void fft(double[] real, double[] imag) {
		int n = real.length;

		if (Integer.bitCount(n) != 1) {
			throw new IllegalArgumentException("FFT length must be power of two");
		}

		// Bit-reversal permutation
		int j = 0;
		for (int i = 1; i < n; i++) {
			int bit = n >> 1;
			while ((j & bit) != 0) {
				j ^= bit;
				bit >>= 1;
			}
			j |= bit;

			if (i < j) {
				double tempReal = real[i];
				real[i] = real[j];
				real[j] = tempReal;

				double tempImag = imag[i];
				imag[i] = imag[j];
				imag[j] = tempImag;
			}
		}

		// Cooley-Tukey FFT
		for (int len = 2; len <= n; len <<= 1) {
			double angle = -2 * Math.PI / len;
			double wlenReal = Math.cos(angle);
			double wlenImag = Math.sin(angle);

			for (int i = 0; i < n; i += len) {
				double wReal = 1;
				double wImag = 0;
				for (int j1 = 0; j1 < len / 2; j1++) {
					int u = i + j1;
					int v = i + j1 + len / 2;

					double realV = real[v] * wReal - imag[v] * wImag;
					double imagV = real[v] * wImag + imag[v] * wReal;

					double realU = real[u];
					double imagU = imag[u];

					real[v] = realU - realV;
					imag[v] = imagU - imagV;

					real[u] = realU + realV;
					imag[u] = imagU + imagV;

					// Update w
					double nextWReal = wReal * wlenReal - wImag * wlenImag;
					double nextWImag = wReal * wlenImag + wImag * wlenReal;
					wReal = nextWReal;
					wImag = nextWImag;
				}
			}
		}
	}
	
	public static void startSineSynth() {
		if (synthThread != null && synthThread.isAlive())
			return;

		synthThread = new Thread(() -> {
			try {
				AudioFormat format = new AudioFormat(SAMPLE_RATE, 16, 1, true, true);
				SourceDataLine line = AudioSystem.getSourceDataLine(format);
				line.open(format, 1024);
				line.start();

				byte[] buffer = new byte[1024];

				while (synthesize) {
					double[] freqs;
					synchronized (fftMagnitudes) {
						freqs = extractFundamentals(fftMagnitudes, 80, 1200, noiseThreshold);
					}

					for (int i = 0; i < buffer.length / 2; i++) {
						double sample = 0;
						for (double freq : freqs) {
							sample += Math.sin(2 * Math.PI * freq * i / SAMPLE_RATE);
						}
						sample /= freqs.length;
						short val = (short) (sample * 3000); // adjust loudness

						buffer[2 * i] = (byte) ((val >> 8) & 0xFF);
						buffer[2 * i + 1] = (byte) (val & 0xFF);
					}

					line.write(buffer, 0, buffer.length);
				}

				line.drain();
				line.stop();
				line.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
		});
		synthThread.start();
	}

	public static void startSineSynth2() {
		if (synthThread != null && synthThread.isAlive())
			return;

		synthThread = new Thread(() -> {
			try {
				AudioFormat format = new AudioFormat(SAMPLE_RATE, 16, 1, true, true);
				SourceDataLine line = AudioSystem.getSourceDataLine(format);
				line.open(format, 1024);
				line.start();

				byte[] buffer = new byte[1024];

				while (synthesize) {
					double[] freqs;
					synchronized (fftMagnitudes) {
						freqs = extractFundamentals(fftMagnitudes, 80, 1200, noiseThreshold);
					}

					for (int i = 0; i < buffer.length / 2; i++) {
						double sample = 0;
						for (double freq : freqs) {
							sample += Math.sin(2 * Math.PI * freq * i / SAMPLE_RATE);
						}
						sample /= freqs.length;
						short val = (short) (sample * 3000); // adjust loudness

						buffer[2 * i] = (byte) ((val >> 8) & 0xFF);
						buffer[2 * i + 1] = (byte) (val & 0xFF);
					}

					line.write(buffer, 0, buffer.length);
				}

				line.drain();
				line.stop();
				line.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
		});
		synthThread.start();
	}

	public static void stopSineSynth() {
		synthesize = false;
	}

	public static double[] extractFundamentals(double[] magnitudes, double minFreq, double maxFreq, double threshold) {
		ArrayList<Double> fundamentals = new ArrayList<>();
		int fftSize = magnitudes.length * 2;
		boolean[] used = new boolean[magnitudes.length];

		for (int i = 1; i < magnitudes.length; i++) {
			if (used[i] || magnitudes[i] < threshold)
				continue;
			double freq = i * SAMPLE_RATE / (double) fftSize;

			if (freq < minFreq || freq > maxFreq)
				continue;

			// Mark harmonics so we don't double-count
			for (int h = 1; h * i < magnitudes.length; h++) {
				used[h * i] = true;
			}
			fundamentals.add(freq);
		}

		double[] ret = fundamentals.stream().mapToDouble(d -> d).toArray();
		
		// Normalize
		double max = Arrays.stream(magnitudes).max().orElse(1);
		for (int i = 0; i < magnitudes.length; i++) {
			ret[i] /= max;
		}
		return ret;
	}

	@Override
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);

		Graphics2D g2d = (Graphics2D) g;
		g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		g.setColor(backgroundColor);
		g.fillRect(0, 0, 2000, 2000);

		paintLoop(g2d);
	}

	public GuitarExperiment() {
		KeyListener d = new MyKeyListener();
		addKeyListener(d);
		MouseListener mML = new MyMouseListener();
		addMouseListener(mML);
		MouseMotionListener mMML = new MyMouseMotionListener();
		addMouseMotionListener(mMML);
		setFocusable(true);
	}

	public static void main(String[] args) {
		JFrame frame = new JFrame("Guitar Rolling Waveform");
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.add(new GuitarExperiment());
		frame.setSize(width, height);
		frame.setVisible(true);
		initialize();
		while (true) {
			currentFrame++;
			frame.repaint();
			mainLoop();
			try {
				Thread.sleep(frameRate);
			} catch (Exception e) {
				System.out.println("Error!");
			}
		}
	}

	public class MyKeyListener implements KeyListener {
		@Override
		public void keyTyped(KeyEvent e) {
		}

		@Override
		public void keyPressed(KeyEvent e) {
			if (e.getKeyChar() == 's' || e.getKeyChar() == 'S') {
				synthesize = !synthesize;
				if (synthesize) {
					System.out.println("Synthesizing ON");
					startSineSynth();
				} else {
					System.out.println("Synthesizing OFF");
					stopSineSynth();
				}
			}
		}

		@Override
		public void keyReleased(KeyEvent e) {
		}
	}

	public class MyMouseListener implements MouseListener {
		public void mouseExited(MouseEvent e) {
		}

		public void mouseEntered(MouseEvent e) {
		}

		public void mouseReleased(MouseEvent e) {
		}

		public void mousePressed(MouseEvent e) {
		}

		public void mouseClicked(MouseEvent e) {
		}
	}

	public class MyMouseMotionListener implements MouseMotionListener {
		public void mouseDragged(MouseEvent e) {
		}

		public void mouseMoved(MouseEvent e) {
		}
	}
}
