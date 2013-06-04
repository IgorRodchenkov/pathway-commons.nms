// $Id: CytoscapeRenderer.java,v 1.17 2009/06/30 14:43:55 grossb Exp $
//------------------------------------------------------------------------------
/** Copyright (c) 2008 Memorial Sloan-Kettering Cancer Center.
 **
 ** Code written by: Ethan Cerami, Benjamin Gross
 ** Authors: Ethan Cerami, Gary Bader, Benjamin Gross, Chris Sander
 **
 ** This library is free software; you can redistribute it and/or modify it
 ** under the terms of the GNU Lesser General Public License as published
 ** by the Free Software Foundation; either version 2.1 of the License, or
 ** any later version.
 **
 ** This library is distributed in the hope that it will be useful, but
 ** WITHOUT ANY WARRANTY, WITHOUT EVEN THE IMPLIED WARRANTY OF
 ** MERCHANTABILITY OR FITNESS FOR A PARTICULAR PURPOSE.  The software and
 ** documentation provided hereunder is on an "as is" basis, and
 ** Memorial Sloan-Kettering Cancer Center
 ** has no obligations to provide maintenance, support,
 ** updates, enhancements or modifications.  In no event shall
 ** Memorial Sloan-Kettering Cancer Center
 ** be liable to any party for direct, indirect, special,
 ** incidental or consequential damages, including lost profits, arising
 ** out of the use of this software and its documentation, even if
 ** Memorial Sloan-Kettering Cancer Center
 ** has been advised of the possibility of such damage.  See
 ** the GNU Lesser General Public License for more details.
 **
 ** You should have received a copy of the GNU Lesser General Public License
 ** along with this library; if not, write to the Free Software Foundation,
 ** Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA.
 **/
package org.mskcc.nms.render;

// imports
import org.mskcc.pathdb.xdebug.XDebug;
import org.mskcc.pathdb.model.XmlRecordType;
import org.mskcc.pathdb.sql.assembly.XmlAssembly;
import org.mskcc.pathdb.sql.assembly.XmlAssemblyFactory;
import org.mskcc.pathdb.sql.assembly.AssemblyException;
import org.mskcc.pathdb.util.ExternalDatabaseConstants;
import org.mskcc.pathdb.schemas.binary_interaction.util.BinaryInteractionUtil;
import org.mskcc.pathdb.schemas.binary_interaction.assembly.BinaryInteractionAssembly;
import org.mskcc.pathdb.schemas.binary_interaction.assembly.BinaryInteractionAssemblyFactory;

import org.mskcc.biopax_plugin.util.biopax.BioPaxUtil;
import org.mskcc.biopax_plugin.util.biopax.BioPaxConstants;
import org.mskcc.biopax_plugin.util.biopax.BioPaxEntityParser;
import org.mskcc.biopax_plugin.style.BioPaxVisualStyleUtil;
import org.mskcc.biopax_plugin.mapping.MapNodeAttributes;
import org.mskcc.biopax_plugin.util.cytoscape.LayoutUtil;

import org.cytoscape.coreplugin.cpath2.cytoscape.BinarySifVisualStyleUtil;

import cytoscape.Cytoscape;
import cytoscape.CyNetwork;
import cytoscape.view.CyNetworkView;
import cytoscape.data.CyAttributes;
import cytoscape.data.readers.GraphReader;
import cytoscape.task.TaskMonitor;
import cytoscape.ding.DingNetworkView;
import cytoscape.ding.CyGraphLOD;
import cytoscape.visual.VisualStyle;
import cytoscape.visual.VisualMappingManager;

import ding.view.DingCanvas;
import ding.view.DGraphView;

import org.apache.log4j.Logger;

import org.jdom.Element;
import org.jdom.Namespace;
import org.jdom.JDOMException;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringReader;
import java.util.HashSet;
import java.util.ArrayList;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.GraphicsEnvironment;
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;


/**
 * Generates Neighborhood Map.
 *
 * @author Benjamin Gross
 */
public class CytoscapeRenderer {

	// some statics
	private static java.awt.Color BACKGROUND_COLOR = new java.awt.Color(204,204,255);
    private static Logger log = Logger.getLogger(CytoscapeRenderer.class);
	private static int SVG_WIDTH_SMALL = 150;
	private static int SVG_HEIGHT_SMALL = 150;
	private static int SVG_WIDTH_LARGE = 585;
	private static int SVG_HEIGHT_LARGE = 540;

	// member vars
	private static int WIDTH;
	private static int HEIGHT;
	private static String UNWANTED_INTERACTIONS_STRING;
	private static ArrayList<String> UNWANTED_INTERACTIONS;
	private static String UNWANTED_SMALL_MOLECULES_STRING;
	private static ArrayList<String> UNWANTED_SMALL_MOLECULES;

    /**
     * Generates neighborhood map image
     *
     * @param request  Http Servlet Request.
     * @param response Http Servlet Response.
	 * @param responseOutputStream OutputStream
     * @throws Exception All Exceptions.
     */
    public static void renderNeighborhoodMap(HttpServletRequest request, HttpServletResponse response, ServletOutputStream responseOutputStream) throws Exception {

		log.info("************************ CytoscapeRenderer.renderNeighborhoodMap()");

		// get args
		setMemberVars(request);

		log.info("************************ CytoscapeRenderer.renderNeighborhoodMap(), width: " + WIDTH + " , height: " + HEIGHT);
		log.info("************************ CytoscapeRenderer.renderNeighborhoodMap(), unwanted_interactions: " + UNWANTED_INTERACTIONS_STRING);
		log.info("************************ CytoscapeRenderer.renderNeighborhoodMap(), unwanted_small_molecules: " + UNWANTED_SMALL_MOLECULES_STRING);

		// get biopax assembly
		XmlAssembly biopaxAssembly = XmlAssemblyFactory.createXmlAssembly(request.getParameter("data"),
																		  XmlRecordType.BIO_PAX,
																		  1, new XDebug());

		//log.info("************************ CytoscapeRenderer.subExecute(), biopax assembly string: " + biopaxAssembly.getXmlString());
		
		// get binary sif tmpFile
		File sifFile = getSIFFile(biopaxAssembly);

		if (sifFile == null) {
			if (WIDTH == SVG_WIDTH_SMALL) {
				writeMapToResponse(new ImageIcon(CytoscapeRenderer.class.getResource("resources/too-many-neighbors-found-thumbnail.png")), SVG_WIDTH_SMALL, SVG_HEIGHT_SMALL, response);
			}
			else {
				writeMapToResponse(new ImageIcon(CytoscapeRenderer.class.getResource("resources/too-many-neighbors-found.png")), SVG_WIDTH_LARGE, SVG_HEIGHT_LARGE, response);
			}
			return;
		}

		// get CyNetwork
		CyNetwork cyNetwork = getCyNetwork(sifFile, biopaxAssembly);

		// remove sif
		sifFile.delete();

		// post process the network (layout, apply style, etc)
		CyNetworkView cyNetworkView = postProcessCyNetwork(cyNetwork);

		// write out png 
		writeMapToResponse(response, responseOutputStream, cyNetworkView);
    }

	/**
	 * Set member vars
	 *
     * @param request HttpServletRequest
	 */
	private static void setMemberVars(HttpServletRequest request) {

		WIDTH = Integer.parseInt(request.getParameter("width"));
		HEIGHT = Integer.parseInt(request.getParameter("height"));

		UNWANTED_INTERACTIONS = new ArrayList<String>();
		UNWANTED_INTERACTIONS_STRING = request.getParameter("unwanted_interactions");
		if (UNWANTED_INTERACTIONS_STRING != null && UNWANTED_INTERACTIONS_STRING.length() > 0) {
			String[] unwantedInteractionsArray = UNWANTED_INTERACTIONS_STRING.split(" ");
			if (unwantedInteractionsArray.length > 0) {
				for (String unwantedInteraction : unwantedInteractionsArray) {
					UNWANTED_INTERACTIONS.add(unwantedInteraction);
				}
			}
		}
		else {
			UNWANTED_INTERACTIONS_STRING = "";
		}

		UNWANTED_SMALL_MOLECULES = new ArrayList<String>();
		UNWANTED_SMALL_MOLECULES_STRING = request.getParameter("unwanted_small_molecules");
		if (UNWANTED_SMALL_MOLECULES_STRING != null && UNWANTED_SMALL_MOLECULES_STRING.length() > 0) {
			String[] unwantedSmallMoleculesArray = UNWANTED_SMALL_MOLECULES_STRING.split(" ");
			if (unwantedSmallMoleculesArray.length > 0) {
				for (String unwantedSmallMolecule : unwantedSmallMoleculesArray) {
					UNWANTED_SMALL_MOLECULES.add(unwantedSmallMolecule);
				}
			}
		}
		else {
			UNWANTED_SMALL_MOLECULES_STRING = "";
		}
	}

	/**
	 * Given a set of cpath ids, returns a binary sif file.
	 *
	 * @param biopaxAssembly XmlAssembly
	 * @return File
	 * @throws IOException
	 */
	private static File getSIFFile(XmlAssembly biopaxAssembly) throws AssemblyException, IOException {

		log.info("************************ CytoscapeRenderer.getSIFFile()");

		// get binary interaction assembly from biopax
		BinaryInteractionAssembly sifAssembly =
			BinaryInteractionAssemblyFactory.createAssembly(BinaryInteractionAssemblyFactory.AssemblyType.SIF,
															BinaryInteractionUtil.getRuleTypes(),
															biopaxAssembly.getXmlString());

		log.info("************************ CytoscapeRenderer.getSIFFile: sif assembly: " + sifAssembly);

		//log.info("************************ CytoscapeRenderer.getSIFFile: sif assembly string before filter:\n");
		//log.info(sifAssembly.getBinaryInteractionString());

		// filter out unwanted interactions
		HashSet<String> filteredBinaryInteractionsSet = new HashSet<String>();
		String[] binaryInteractionStringArray = sifAssembly.getBinaryInteractionString().split("\n");
		for (String binaryInteractionString : binaryInteractionStringArray) {
			if (binaryInteractionString != null) {
				if (UNWANTED_INTERACTIONS.size() > 0) {
					// sif format:  ID\tINTERACTION_TYPE\tID
					String[] components = binaryInteractionString.split("\t");
					if (components.length == 3) {
						if (!UNWANTED_INTERACTIONS.contains(components[1])) {
							filteredBinaryInteractionsSet.add(binaryInteractionString);
						}
					}
				}
				else {
					filteredBinaryInteractionsSet.add(binaryInteractionString);
				}
			}
		}

		// filter out unwanted small molecules
		if (UNWANTED_SMALL_MOLECULES.size() > 0) {
			HashSet<String> filteredBinaryInteractionsSetClone = (HashSet<String>)(filteredBinaryInteractionsSet.clone());
			for (String binaryInteraction : filteredBinaryInteractionsSetClone) {
				// sif format:  ID\tINTERACTION_TYPE\tID
				String[] components = binaryInteraction.split("\t");
				if (UNWANTED_SMALL_MOLECULES.contains(components[0]) ||
					UNWANTED_SMALL_MOLECULES.contains(components[2])) {
					filteredBinaryInteractionsSet.remove(binaryInteraction);
				}
			}
		}

		// create filteredBinaryInteractions string buffer
		StringBuffer filteredBinaryInteractions = new StringBuffer();
		for (String binaryInteraction : filteredBinaryInteractionsSet) {
			filteredBinaryInteractions.append(binaryInteraction + "\n");
		}

		log.info("************************ CytoscapeRenderer.getSIFFile: sif assembly string size after filter: " + filteredBinaryInteractions.toString().length());

		// if filtered interactions is size 0, bail
		if (filteredBinaryInteractions.toString().length() == 0) {
			return null;
		}
		
		// create tmp file
		String tmpDir = System.getProperty("java.io.tmpdir");
		File tmpFile = File.createTempFile("temp", ".sif", new File(tmpDir));

		// get data to write into temp file
		FileWriter writer = new FileWriter(tmpFile);

		writer.write(filteredBinaryInteractions.toString());
		writer.close();

		// outta here
		return tmpFile;
	}

	/**
	 * Given a sif file, returns a CyNetwork.
	 *
	 * @param sifFile File
	 * @param biopaxAssembly XmlAssembly
	 * @return CyNetwork
	 * @throws IOException
	 * @throws JDOMException
	 */
	private static CyNetwork getCyNetwork(File sifFile, XmlAssembly biopaxAssembly) throws IOException, JDOMException {

		log.info("************************ CytoscapeRenderer.getCyNetwork(), biopaxAssembly: " + biopaxAssembly + " , sifFile: " + sifFile.getAbsolutePath());

		// create cytoscape network
		GraphReader reader = Cytoscape.getImportHandler().getReader(sifFile.getAbsolutePath());
		CyNetwork cyNetwork = Cytoscape.createNetwork(reader, false, null);

		// init the attributes
        CyAttributes nodeAttributes = Cytoscape.getNodeAttributes();
        MapNodeAttributes.initAttributes(nodeAttributes);

        // specify that this is a BINARY_NETWORK
        Cytoscape.getNetworkAttributes().setAttribute(cyNetwork.getIdentifier(),
													  BinarySifVisualStyleUtil.BINARY_NETWORK, Boolean.TRUE);

		// setup node attributes
		StringReader strReader = new StringReader(biopaxAssembly.getXmlString());
		BioPaxUtil bpUtil = new BioPaxUtil(strReader, new NullTaskMonitor());
		ArrayList<Element> peList = bpUtil.getPhysicalEntityList();
		Namespace ns = Namespace.getNamespace("http://www.w3.org/1999/02/22-rdf-syntax-ns#");
		for (Element element : peList) {
			String id = element.getAttributeValue("ID", ns);
			if (id != null) {
				id = id.replaceAll("CPATH-", "");
				MapNodeAttributes.mapNodeAttribute(element, id, nodeAttributes, bpUtil);
				// update label for display
				String newLabel = null;
				String type = nodeAttributes.getStringAttribute(id, MapNodeAttributes.BIOPAX_ENTITY_TYPE);
				// for pe, first use gene symbol, then synonym or short name
				if (type != null && (type.equalsIgnoreCase(BioPaxConstants.DNA) || type.equalsIgnoreCase(BioPaxConstants.RNA) ||
									 type.equalsIgnoreCase(BioPaxConstants.PROTEIN) || type.equalsIgnoreCase(BioPaxConstants.PHYSICAL_ENTITY) ||
									 type.equalsIgnoreCase(BioPaxConstants.SMALL_MOLECULE) || type.equalsIgnoreCase("Physical Entity"))) {
				        String geneSymbolProperty = MapNodeAttributes.BIOPAX_XREF_PREFIX + ExternalDatabaseConstants.GENE_SYMBOL;
				        String geneSymbol = nodeAttributes.getStringAttribute(id, geneSymbolProperty);
				        if (geneSymbol != null && geneSymbol.length() > 0) {
					    newLabel = geneSymbol;
					}
					else {
					    java.util.List synList = nodeAttributes.getListAttribute(id, MapNodeAttributes.BIOPAX_SYNONYMS);
					    if (synList != null && synList.size() > 0 && ((String)(synList.get(0))).length() > 0) {
						newLabel = (String)synList.get(0);
					    }
					    else {
						String shortName = nodeAttributes.getStringAttribute(id, MapNodeAttributes.BIOPAX_SHORT_NAME);
						if (shortName != null && shortName.length() > 0) {
							newLabel = shortName;
						}
					    }
					}
				}
				// for complex, use short name and truncate after "complex at site" if possible
				else if (type != null && type.equalsIgnoreCase(BioPaxConstants.COMPLEX)) {
					String shortName = nodeAttributes.getStringAttribute(id, MapNodeAttributes.BIOPAX_SHORT_NAME);
					if (shortName != null && shortName.length() > 0) {
						String[] parts = shortName.split("complex at site");
						newLabel = (parts != null && parts.length > 0) ? parts[0] : shortName;
					}
				}
				if (newLabel != null) {
					nodeAttributes.setAttribute(id, BioPaxVisualStyleUtil.BIOPAX_NODE_LABEL, newLabel);
				}
			}
		}

		// outta here
		return cyNetwork;
	}

	/**
	 * Given a binary sif file, creates a CyNetwork.
	 *
	 * @param cyNetwork CyNetwork
	 * @return CyNetworkView
	 */
	private static CyNetworkView postProcessCyNetwork(CyNetwork cyNetwork) {

		log.info("************************ CytoscapeRenderer.postProcessCyNetwork(), cyNetwork: " + cyNetwork);

		//  create view - use local create view option, so that we don't mess up the visual style.
		LayoutUtil layoutAlgorithm = new LayoutUtil();
		final DingNetworkView dView = new DingNetworkView(cyNetwork, "");
		dView.setGraphLOD(new CyGraphLOD());

		// set canvas attributes
		DingCanvas innerCanvas = (DingCanvas)dView.getComponent();
		innerCanvas.setOpaque(true);
		innerCanvas.setBackground(BACKGROUND_COLOR);
		innerCanvas.setBounds(0, 0, WIDTH, HEIGHT);

		// setup visual style
		VisualStyle visualStyle = BinarySifVisualStyleUtil.getVisualStyle();
		VisualMappingManager VMM = Cytoscape.getVisualMappingManager();
		dView.setVisualStyle(visualStyle.getName());
		VMM.setVisualStyle(visualStyle);
		VMM.setNetworkView(dView);

		// layout
		layoutAlgorithm.doLayout(dView);
		dView.redrawGraph(false, true);
		dView.fitContent();

		// zoom just a tad out
		dView.setZoom(dView.getZoom() * 0.90);

		// outta here
		return dView;
	}

	/**
	 * Given a CyNetwork view, generates a png file.
	 * 
	 * @param response HttpServletResponse
	 * @param responseOutputStream OutputStream
	 * @param cyNetworkView CyNetworkView
	 * @throws IOException
	 */
	private static void writeMapToResponse(HttpServletResponse response, ServletOutputStream responseOutputStream, CyNetworkView cyNetworkView) throws IOException {

		log.info("************************ CytoscapeRenderer.writeMapToResponse, cyNetworkView: " + cyNetworkView);

		double scale = 1.0;

		// needed to prevent java.lang.unsatisfiedLinkError: initGVIDS
		GraphicsEnvironment.getLocalGraphicsEnvironment();

		DGraphView dView = (DGraphView)cyNetworkView;
		DingCanvas innerCanvas = (DingCanvas)dView.getComponent();
		innerCanvas.setOpaque(true);
			
		int width  = (int) (innerCanvas.getWidth() * scale);
		int height = (int) (innerCanvas.getHeight() * scale);

		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = (Graphics2D) image.getGraphics();
		g.scale(scale, scale);
		innerCanvas.print(g);
		g.dispose();

		response.setContentType("image/png");
		ImageIO.write(image, "png", responseOutputStream);
	}

    /**
	 * Writes predefined image image to response.
	 *
	 * @param icon imageIcon
	 * @param width int
	 * @param height int
	 * @param response HttpServletResponse
	 * @throws IOException
	 */
	private static void writeMapToResponse(ImageIcon icon, int width, int height, HttpServletResponse response) throws IOException {

		// create buffered image
		final BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		final Graphics2D g2d = image.createGraphics();
		g2d.drawImage(icon.getImage(), 0, 0, width, height, null);
		g2d.dispose();

		// write out the image bytes
		response.setContentType("image/png");
		ImageIO.write(image, "png", response.getOutputStream());
	}
}

class NullTaskMonitor implements TaskMonitor {

    public void setPercentCompleted(int i) throws IllegalArgumentException {
    }

    public void setEstimatedTimeRemaining(long l) throws IllegalThreadStateException {
    }

    public void setException(Throwable throwable, String string)
            throws IllegalThreadStateException {
    }

    public void setException(Throwable throwable, String string, String string1)
            throws IllegalThreadStateException {
    }

    public void setStatus(String string) throws IllegalThreadStateException, NullPointerException {
    }
}
