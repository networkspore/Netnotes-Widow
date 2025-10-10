package io.netnotes.gui.fx.display.javafxsvg;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.Buffer;
import java.nio.ByteBuffer;

import javafx.stage.Screen;
import org.apache.batik.transcoder.TranscoderException;
import org.apache.batik.transcoder.TranscoderInput;

import com.sun.javafx.iio.ImageFrame;
import com.sun.javafx.iio.ImageStorage;

import com.sun.javafx.iio.common.ImageLoaderImpl;

import static org.apache.batik.transcoder.SVGAbstractTranscoder.KEY_HEIGHT;
import static org.apache.batik.transcoder.SVGAbstractTranscoder.KEY_WIDTH;
import static org.apache.batik.transcoder.SVGAbstractTranscoder.KEY_MAX_WIDTH;
import static org.apache.batik.transcoder.SVGAbstractTranscoder.KEY_MAX_HEIGHT;

public class SvgImageLoader extends ImageLoaderImpl {

	private static final double DEFAULT_SIZE = 400;

	private static final int BYTES_PER_PIXEL = 4; // RGBA

	private final InputStream input;

	private float maxPixelScale = 0;

	protected SvgImageLoader(InputStream input) {
		super(SvgDescriptor.getInstance());

		if (input == null) {
			throw new IllegalArgumentException("input == null!");
		}

		this.input = input;
	}

	@Override
	public ImageFrame load(int imageIndex, double width, double height, 
						boolean preserveAspectRatio, boolean smooth, 
						float devicePixelRatio, float requestedPixelScale) throws IOException {
		if (0 != imageIndex) {
			return null;
		}
		
		float pixelScale = requestedPixelScale > 0 ? requestedPixelScale : devicePixelRatio;
		
		double targetWidth = width > 0 ? width : DEFAULT_SIZE;
		double targetHeight = height > 0 ? height : DEFAULT_SIZE;
		
		try {
			BufferedImage bufferedImage;
			
			if (preserveAspectRatio && width > 0 && height > 0) {
				// Let Batik preserve aspect ratio by constraining both dimensions
				bufferedImage = getTranscodedImagePreservingAspect(
					targetWidth * pixelScale, 
					targetHeight * pixelScale
				);
			} else {
				// Render at exact dimensions
				bufferedImage = getTranscodedImage(
					targetWidth * pixelScale, 
					targetHeight * pixelScale
				);
			}
			
			// Create frame from buffered image (same for both paths)
			Buffer imageData = getImageData(bufferedImage);

			return new ImageFrame(
				ImageStorage.ImageType.RGBA, 
				imageData, 
				bufferedImage.getWidth(),
				bufferedImage.getHeight(), 
				getStride(bufferedImage), 
				pixelScale, 
				null
			);
			
		} catch (TranscoderException ex) {
			throw new IOException(ex);
		}
	}

	public float getPixelScale() {
		if (maxPixelScale == 0) {
			maxPixelScale = calculateMaxRenderScale();
		}
		return maxPixelScale;
	}

	public float calculateMaxRenderScale() {
		float maxRenderScale = 0;
		ScreenHelper.ScreenAccessor accessor = ScreenHelper.getScreenAccessor();
		for (Screen screen : Screen.getScreens()) {
			maxRenderScale = Math.max(maxRenderScale, accessor.getRenderScale(screen));
		}
		return maxRenderScale;
	}


	private BufferedImage getTranscodedImage(double width, double height) throws TranscoderException {
		BufferedImageTranscoder trans = new BufferedImageTranscoder(BufferedImage.TYPE_INT_ARGB);
		trans.addTranscodingHint(KEY_WIDTH, (float) width);
		trans.addTranscodingHint(KEY_HEIGHT, (float) height);
		trans.transcode(new TranscoderInput(this.input), null);
		return trans.getBufferedImage();
	}

	private BufferedImage getTranscodedImagePreservingAspect(double maxWidth, double maxHeight) 
			throws TranscoderException {
		BufferedImageTranscoder trans = new BufferedImageTranscoder(BufferedImage.TYPE_INT_ARGB);
	
		// Set max dimensions - Batik will preserve aspect ratio automatically
		trans.addTranscodingHint(KEY_MAX_WIDTH, (float) maxWidth);
		trans.addTranscodingHint(KEY_MAX_HEIGHT, (float) maxHeight);
		
		trans.transcode(new TranscoderInput(this.input), null);
		return trans.getBufferedImage();
	}


	private int getStride(BufferedImage bufferedImage) {
		return bufferedImage.getWidth() * BYTES_PER_PIXEL;
	}

	private Buffer getImageData(BufferedImage bufferedImage) {
		int[] rgb = bufferedImage.getRGB(0, 0, bufferedImage.getWidth(), bufferedImage.getHeight(), null, 0,
				bufferedImage.getWidth());

		byte[] imageData = new byte[getStride(bufferedImage) * bufferedImage.getHeight()];

		copyColorToBytes(rgb, imageData);
		return ByteBuffer.wrap(imageData);
	}

	private void copyColorToBytes(int[] rgb, byte[] imageData) {
		if (rgb.length * BYTES_PER_PIXEL != imageData.length) {
			throw new ArrayIndexOutOfBoundsException();
		}

		ByteBuffer byteBuffer = ByteBuffer.allocate(Integer.BYTES);

		for (int i = 0; i < rgb.length; i++) {
			byte[] bytes = byteBuffer.putInt(rgb[i]).array();

			int dataOffset = BYTES_PER_PIXEL * i;
			imageData[dataOffset] = bytes[1];
			imageData[dataOffset + 1] = bytes[2];
			imageData[dataOffset + 2] = bytes[3];
			imageData[dataOffset + 3] = bytes[0];

			byteBuffer.clear();
		}
	}


	

	@Override
	public void dispose() {
		// Nothing to do
	}
}
