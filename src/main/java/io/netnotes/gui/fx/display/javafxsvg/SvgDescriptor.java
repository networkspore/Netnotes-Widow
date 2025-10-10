package io.netnotes.gui.fx.display.javafxsvg;

import com.sun.javafx.iio.common.ImageDescriptor;

public class SvgDescriptor extends ImageDescriptor {

	private static final String formatName = "SVG";

	private static final String[] extensions = { "svg" };

	private static final Signature[] signatures = {
			new Signature("<svg".getBytes()), new Signature("<?xml".getBytes()) };

	private static final String[] mimeSubtypes = {"svg+xml"};

	private static ImageDescriptor theInstance = null;
		//String var1, String[] var2, ImageFormatDescription.Signature[] var3, String[] var4
	private SvgDescriptor() {
		super(formatName, extensions, signatures, mimeSubtypes);
	}

	public static synchronized ImageDescriptor getInstance() {
		if (theInstance == null) {
			theInstance = new SvgDescriptor();
		}
		return theInstance;
	}
}
