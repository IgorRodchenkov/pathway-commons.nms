// $Id: WebService.java,v 1.3 2008/09/09 16:22:09 grossb Exp $
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
package org.mskcc.nms.servlet;

// imports
import org.mskcc.nms.render.CytoscapeRenderer;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.OutputStream;

public class WebService extends HttpServlet {

    /**
     * Shutdown the Servlet.
     */
    public void destroy() {
        super.destroy();
        System.err.println("Shutting Down the Neighborhood Map Server...");
    }

    /**
     * Initializes Servlet with parameters in web.xml file.
     *
     * @throws javax.servlet.ServletException Servlet Initialization Error.
     */
    public void init() throws ServletException {
        super.init();
        System.out.println("Starting up the Neighborhood Map Server...");
    }

    protected void doGet(HttpServletRequest httpServletRequest,
						 HttpServletResponse httpServletResponse) throws ServletException, IOException {
        processClient(httpServletRequest, httpServletResponse);
    }

    protected void doPost(HttpServletRequest httpServletRequest,
						  HttpServletResponse httpServletResponse) throws ServletException, IOException {
        processClient(httpServletRequest, httpServletResponse);
    }

    private void processClient(HttpServletRequest httpServletRequest,
							   HttpServletResponse httpServletResponse) throws IOException {
        ServletOutputStream responseOutputStream = httpServletResponse.getOutputStream();
        String data = httpServletRequest.getParameter("data");
		String width = httpServletRequest.getParameter("width");
		String height = httpServletRequest.getParameter("height");
        if (data != null && width != null && height != null) {
            try {
				Integer.parseInt(width);
				Integer.parseInt(height);
				CytoscapeRenderer.renderNeighborhoodMap(httpServletRequest, httpServletResponse, responseOutputStream);
            } catch (Exception e) {
                outputError(responseOutputStream, "internal error:  " + e.getMessage());
            } finally {
                responseOutputStream.flush();
                responseOutputStream.close();
            }
        } else {
            if (data == null) outputMissingParameterError(responseOutputStream, "data");
            if (width == null) outputMissingParameterError(responseOutputStream, "width");
            if (height == null) outputMissingParameterError(responseOutputStream, "height");
        }
    }

    private void outputError(ServletOutputStream responseOutputStream, String msg) throws IOException {
        responseOutputStream.print("Error:  " + msg + "\n");
    }

    private void outputMissingParameterError (ServletOutputStream responseOutputStream, String missingParameter) throws IOException {
        outputError (responseOutputStream, "you must specify a " + missingParameter + " parameter.");
    }
}
