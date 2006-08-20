/*
 * Copyright (c) 2000-2002 INSciTE.  All rights reserved
 * INSciTE is on the web at: http://www.hightechkids.org
 * This code is released under GPL; see LICENSE.txt for details.
 */
package fll.pdf.report;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfTemplate;
import com.lowagie.text.pdf.PdfWriter;

import fll.Queries;
import fll.Utilities;

import java.awt.Color;

import java.io.IOException;
import java.io.OutputStream;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import java.text.NumberFormat;
import java.text.ParseException;

import java.util.Iterator;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;


/**
 * Code for finalComputedScores_pdf.jsp
 *
 * @version $Revision$
 */
public final class FinalComputedScores extends PdfPageEventHelper {
  
  private final String _tournament;
  private final String _challengeTitle;
  private String _division;
  private PdfPTable _header;
  private PdfTemplate _tpl;
  private BaseFont _headerFooterFont;
  private final org.w3c.dom.Document _challengeDocument;

  public FinalComputedScores(final org.w3c.dom.Document challengeDoc,
                             final String tournament) {
    _challengeDocument = challengeDoc;
    Element root = challengeDoc.getDocumentElement();
    _challengeTitle = root.getAttribute("title");
    _tournament = tournament;
  }

  private void updateHeader(final PdfWriter writer, final Document document) {
    // initialization of the header table
    _header = new PdfPTable(2);
    Phrase p = new Phrase();
    Chunk ck = new Chunk(_challengeTitle + "\nFinal Computed Scores", TIMES_12PT_NORMAL);
    p.add(ck);
    _header.getDefaultCell().setBorderWidth(0);
    _header.addCell(p);
    _header.getDefaultCell().setHorizontalAlignment(com.lowagie.text.Element.ALIGN_RIGHT);
    _header.addCell(new Phrase(new Chunk("Tournament: " + _tournament + "\nDivision: " + _division, TIMES_12PT_NORMAL)));
    PdfPCell blankCell = new PdfPCell();
    blankCell.setBorder(0);
    blankCell.setBorderWidthTop(1.0f);
    blankCell.setColspan(2);
    _header.addCell(blankCell);
  }
  
  public void onOpenDocument(final PdfWriter writer, final Document document) {
    _headerFooterFont = TIMES_12PT_NORMAL.getBaseFont();

    // initialization of the footer template
    _tpl = writer.getDirectContent().createTemplate(100, 100);
    _tpl.setBoundingBox(new Rectangle(-20, -20, 100, 100));
  }
  
  public void onEndPage(final PdfWriter writer, final Document document) {
    PdfContentByte cb = writer.getDirectContent();
    cb.saveState();
    // write the headertable
    updateHeader(writer, document); // creates the header table with current division, etc.
    _header.setTotalWidth(document.right() - document.left());
    _header.writeSelectedRows(0, -1, document.left(), document.getPageSize().height() - 10, cb);
    
    // compose the footer
    String text = "Page " + writer.getPageNumber() + " of ";
    float textSize = _headerFooterFont.getWidthPoint(text, 12);
    float textBase = document.bottom() - 20;
    cb.beginText();
    cb.setFontAndSize(_headerFooterFont, 12);

    float adjust = _headerFooterFont.getWidthPoint("0", 12);
    cb.setTextMatrix(document.right() - textSize - adjust, textBase);
    cb.showText(text);
    cb.endText();
    cb.addTemplate(_tpl, document.right() - adjust, textBase);
    
    cb.restoreState();
  }

  public void onCloseDocument(final PdfWriter writer, final Document document) {
    _tpl.beginText();
    _tpl.setFontAndSize(_headerFooterFont, 12);
    _tpl.setTextMatrix(0, 0);
    _tpl.showText("" + (writer.getPageNumber() - 1));
    _tpl.endText();
  }

  private static final Font ARIAL_8PT_BOLD = FontFactory.getFont(
                                                                 FontFactory.HELVETICA, 8, Font.BOLD);
  private static final Font ARIAL_8PT_NORMAL = FontFactory.getFont(
                                                                   FontFactory.HELVETICA, 8, Font.NORMAL);
  private static final Font ARIAL_8PT_NORMAL_RED = FontFactory.getFont(
                                                                       FontFactory.HELVETICA, 8, Font.NORMAL, Color.RED);
  private static final Font TIMES_12PT_NORMAL = FontFactory.getFont(FontFactory.TIMES, 12, Font.NORMAL);

  /**
   * Generate the actual report.
   */
  public void generateReport(final Connection connection,
                             final OutputStream out)
    throws SQLException, IOException {

    Statement stmt = null;
    Statement teamsStmt = null;
    ResultSet rawScoreRS = null;
    ResultSet teamsRS = null;
    try {
      // This creates our new PDF document and declares it to be in landscape orientation
      Document pdfDoc = new Document(PageSize.LETTER);
      PdfWriter writer = PdfWriter.getInstance(pdfDoc, out);
      writer.setPageEvent(this);
      
      // Measurements are always in points (72 per inch) - This sets up 1/2 inch margins
      pdfDoc.setMargins(0.5f * 72, 0.5f * 72, 0.5f * 72, 0.5f * 72);
      pdfDoc.open();

      final Element rootElement = _challengeDocument.getDocumentElement();

      final NodeList subjectiveCategories = rootElement.getElementsByTagName("subjectiveCategory");
      stmt = connection.createStatement();
      teamsStmt = connection.createStatement();

      final Iterator divisionIter = Queries.getDivisions(connection).iterator();
      while(divisionIter.hasNext()) {
        _division = (String)divisionIter.next();

        // Figure out how many subjective categories have weights > 0.
        final double[] weights = new double[subjectiveCategories.getLength()];
        final Element[] catElements = new Element[subjectiveCategories.getLength()];
        int nonZeroWeights = 0;
        for(int cat=0; cat<subjectiveCategories.getLength(); cat++) {
          catElements[cat] = (Element)subjectiveCategories.item(cat);
          weights[cat] = Utilities.NUMBER_FORMAT_INSTANCE.parse(catElements[cat].getAttribute("weight")).doubleValue();
          if(weights[cat] > 0.0) {
            nonZeroWeights++;
          }
        }
        // Array of relative widths for the columns of the score page
        // Array length varies with the number of subjective scores weighted > 0.
        float[] relativeWidths = new float[nonZeroWeights + 4];
        relativeWidths[0] = 4f;
        relativeWidths[1] = 1.0f;
        relativeWidths[relativeWidths.length - 2] = 1.5f;
        relativeWidths[relativeWidths.length - 1] = 1.5f;
        for(int i = 2; i < 2 + nonZeroWeights; i++) {
          relativeWidths[i] = 1.5f;
        }
        
        // Create a table to hold all the scores for this division
        PdfPTable divTable = new PdfPTable(relativeWidths);
        divTable.getDefaultCell().setBorder(0);
        divTable.setWidthPercentage(100);

        ///////////////////////////////////////////////////////////////////////
          // Write the table column headers
          ///////////////////////////////////////////////////////////////////////
          PdfPCell teamCell = new PdfPCell(new Phrase("Organization / Team # / Team Name", ARIAL_8PT_BOLD));
          teamCell.setBorder(0);
          teamCell.setVerticalAlignment(com.lowagie.text.Element.ALIGN_MIDDLE);
          divTable.addCell(teamCell);
          divTable.addCell("");

          PdfPCell[] catCells = new PdfPCell[subjectiveCategories.getLength()];
          Paragraph[] catPars = new Paragraph[subjectiveCategories.getLength()];
          for(int cat=0; cat<catElements.length; cat++) {
            if(weights[cat] > 0.0) {
              final String catTitle = catElements[cat].getAttribute("title");

              catPars[cat] = new Paragraph(catTitle, ARIAL_8PT_BOLD);
              catCells[cat] = new PdfPCell(catPars[cat]);
              catCells[cat].setBorder(0);
              catCells[cat].setHorizontalAlignment(com.lowagie.text.Element.ALIGN_CENTER);
              catCells[cat].setVerticalAlignment(com.lowagie.text.Element.ALIGN_MIDDLE);
              divTable.addCell(catCells[cat]);
            }
          }

          Paragraph perfPar = new Paragraph("Performance", ARIAL_8PT_BOLD);
          PdfPCell perfCell = new PdfPCell(perfPar);
          perfCell.setBorder(0);
          perfCell.setHorizontalAlignment(com.lowagie.text.Element.ALIGN_CENTER);
          perfCell.setVerticalAlignment(com.lowagie.text.Element.ALIGN_MIDDLE);
          divTable.addCell(perfCell);

          Paragraph overallScore = new Paragraph("Overall\nScore", ARIAL_8PT_BOLD);
          PdfPCell osCell = new PdfPCell(overallScore);
          osCell.setBorder(0);
          osCell.setHorizontalAlignment(com.lowagie.text.Element.ALIGN_CENTER);
          osCell.setVerticalAlignment(com.lowagie.text.Element.ALIGN_MIDDLE);
          divTable.addCell(osCell);

          ///////////////////////////////////////////////////////////////////////
            // Write a table row with the relative weights of the subjective scores
            ///////////////////////////////////////////////////////////////////////

            Paragraph wPar = new Paragraph("Weight:", ARIAL_8PT_NORMAL);
            PdfPCell wCell = new PdfPCell(wPar);
            wCell.setColspan(2);
            wCell.setBorder(0);
            wCell.setHorizontalAlignment(com.lowagie.text.Element.ALIGN_RIGHT);
            divTable.addCell(wCell);

            PdfPCell[] wCells = new PdfPCell[subjectiveCategories.getLength()];
            Paragraph[] wPars = new Paragraph[subjectiveCategories.getLength()];
            for(int cat=0; cat<catElements.length; cat++) {
              if(weights[cat] > 0.0) {
                wPars[cat] = new Paragraph(Double.toString(weights[cat]), ARIAL_8PT_NORMAL);
                wCells[cat] = new PdfPCell(wPars[cat]);
                wCells[cat].setBorder(0);
                wCells[cat].setHorizontalAlignment(com.lowagie.text.Element.ALIGN_CENTER);
                divTable.addCell(wCells[cat]);
              }
            }

            final Element performanceElement = (Element) rootElement
              .getElementsByTagName("Performance").item(0);
            final double perfWeight = Utilities.NUMBER_FORMAT_INSTANCE.parse(
                                                                             performanceElement.getAttribute("weight")).doubleValue();
            Paragraph perfWeightPar = new Paragraph(Double.toString(perfWeight),
                                                    ARIAL_8PT_NORMAL);
            PdfPCell perfWeightCell = new PdfPCell(perfWeightPar);
            perfWeightCell
              .setHorizontalAlignment(com.lowagie.text.Element.ALIGN_CENTER);
            perfWeightCell.setBorder(0);
            divTable.addCell(perfWeightCell);
        
            divTable.addCell("");

            PdfPCell blankCell = new PdfPCell();
            blankCell.setBorder(0);
            blankCell.setBorderWidthBottom(1.0f);
            blankCell.setColspan(4 + nonZeroWeights);
            divTable.addCell(blankCell);

            divTable.setHeaderRows(3); // Cause the first 3 rows to be repeated on
            // each page - 2 rows text headers and 1 for
            // the horizontal line.

            final StringBuilder query = new StringBuilder();
            query.append("SELECT Teams.Organization,Teams.TeamName,Teams.TeamNumber,FinalScores.OverallScore,FinalScores.performance");
            for(int cat=0; cat<catElements.length; cat++) {
              if(weights[cat] > 0.0) {
                final String catName = catElements[cat].getAttribute("name");
                query.append(",FinalScores." + catName);
              }
            }
            query.append(" FROM Teams,FinalScores");
            query.append(" WHERE FinalScores.TeamNumber = Teams.TeamNumber");
            query.append(" AND FinalScores.Tournament = '" + _tournament + "'");
            query.append(" AND Teams.Division = '" + _division + "'");
            query.append(" ORDER BY FinalScores.OverallScore DESC, Teams.TeamNumber");
            teamsRS = stmt.executeQuery(query.toString());
            while(teamsRS.next()) {
              final int teamNumber = teamsRS.getInt(3);
              final String organization = teamsRS.getString(1);
              final String teamName = teamsRS.getString(2);

              final double totalScore;
              final double ts = teamsRS.getDouble(4);
              if(teamsRS.wasNull()) {
                totalScore = Double.NaN;
              } else {
                totalScore = ts;
              }

              /////////////////////////////////////////////////////////////////////
                // Build a table of data for this team
                /////////////////////////////////////////////////////////////////////
                final PdfPTable curteam = new PdfPTable(relativeWidths);
                curteam.getDefaultCell().setBorder(0);

                // The first row of the team table...
                // First column is organization name
                PdfPCell teamCol = new PdfPCell(new Phrase(organization, ARIAL_8PT_NORMAL));
                teamCol.setBorder(0);
                teamCol.setHorizontalAlignment(com.lowagie.text.Element.ALIGN_LEFT);
                curteam.addCell(teamCol);
                curteam.getDefaultCell().setHorizontalAlignment(com.lowagie.text.Element.ALIGN_RIGHT);
          
                // Second column is "Raw:"
                curteam.addCell(new Phrase("Raw:", ARIAL_8PT_NORMAL));

                // Next, one column containing the raw score for each subjective category with weight > 0
                for(int cat=0; cat<subjectiveCategories.getLength(); cat++) {
                  final Element catElement = (Element)subjectiveCategories.item(cat);
                  final double catWeight = weights[cat];
                  if(catWeight > 0.0) {
                    final String catName = catElement.getAttribute("name");
                    rawScoreRS =
                      stmt.executeQuery("SELECT ComputedTotal"
                                        + " FROM " + catName
                                        + " WHERE TeamNumber = " + teamNumber
                                        + " AND Tournament = '" + _tournament + "'"
                                        + " ORDER BY ComputedTotal DESC");
                    final double rawScore;
                    if(rawScoreRS.next()) {
                      final double v = rawScoreRS.getDouble(1);
                      if(rawScoreRS.wasNull()) {
                        rawScore = Double.NaN;
                      } else {
                        rawScore = v;
                      }
                    } else {
                      rawScore = Double.NaN;
                    }
                    PdfPCell subjCell = new PdfPCell(
                                                     (Double.isNaN(rawScore) ?
                                                      new Phrase("No Score", ARIAL_8PT_NORMAL_RED) :
                                                      new Phrase(SCORE_FORMAT.format(rawScore), ARIAL_8PT_NORMAL)));
                    subjCell.setHorizontalAlignment(com.lowagie.text.Element.ALIGN_CENTER);
                    subjCell.setBorder(0);
                    curteam.addCell(subjCell);
                    rawScoreRS.close();
                  }
                }

                // Column for the highest performance score of the first 3 rounds
                rawScoreRS = stmt.executeQuery("SELECT score FROM performance_seeding_max"
                                               + " WHERE TeamNumber = " + teamNumber
                                               + " AND Tournament = '" + _tournament + "'");
                final double rawScore;
                if(rawScoreRS.next()) {
                  final double v = rawScoreRS.getDouble(1);
                  if(rawScoreRS.wasNull()) {
                    rawScore = Double.NaN;
                  } else {
                    rawScore = v;
                  }
                } else {
                  rawScore = Double.NaN;
                }
                PdfPCell pCell = new PdfPCell(
                                              (Double.isNaN(rawScore) ?
                                               new Phrase("No Score", ARIAL_8PT_NORMAL_RED) :
                                               new Phrase(SCORE_FORMAT.format(rawScore), ARIAL_8PT_NORMAL)));
                pCell.setHorizontalAlignment(com.lowagie.text.Element.ALIGN_CENTER);
                pCell.setBorder(0);
                curteam.addCell(pCell);
                rawScoreRS.close();
          
                // The "Overall score" column is not filled in for raw scores
                curteam.addCell("");

                // The second row of the team table...
                // First column contains the team # and name
                PdfPCell teamNameCol = new PdfPCell(new Phrase(Integer.toString(teamNumber) + " " + teamName, ARIAL_8PT_NORMAL));
                teamNameCol.setBorder(0);
                teamNameCol.setHorizontalAlignment(com.lowagie.text.Element.ALIGN_LEFT);
                curteam.addCell(teamNameCol);
          
                // Second column contains "Scaled:"
                curteam.getDefaultCell().setHorizontalAlignment(com.lowagie.text.Element.ALIGN_RIGHT);
                curteam.addCell(new Phrase("Scaled:", ARIAL_8PT_NORMAL));

                // Next, one column containing the scaled score for each subjective category with weight > 0
                for(int cat=0; cat<subjectiveCategories.getLength(); cat++) {
                  final double catWeight = weights[cat];
                  if(catWeight > 0.0) {
                    final double scaledScore;
                    final double v = teamsRS.getDouble(5 + cat + 1);
                    if(teamsRS.wasNull()) {
                      scaledScore = Double.NaN;
                    } else {
                      scaledScore = v;
                    }

                    PdfPCell subjCell = new PdfPCell(
                                                     (Double.isNaN(scaledScore) ?
                                                      new Phrase("No Score", ARIAL_8PT_NORMAL_RED) :
                                                      new Phrase(SCORE_FORMAT.format(scaledScore), ARIAL_8PT_NORMAL)));
                    subjCell.setHorizontalAlignment(com.lowagie.text.Element.ALIGN_CENTER);
                    subjCell.setBorder(0);
                    curteam.addCell(subjCell);
                  }
                }

                // 2nd to last column has the scaled performance score
                {
                  final double scaledScore;
                  final double v = teamsRS.getDouble(5);
                  if(teamsRS.wasNull()) {
                    scaledScore = Double.NaN;
                  } else {
                    scaledScore = v;
                  }

                  pCell = new PdfPCell((Double.isNaN(scaledScore) ? new Phrase(
                                                                               "No Score", ARIAL_8PT_NORMAL_RED) : new Phrase(SCORE_FORMAT
                                                                                                                              .format(scaledScore), ARIAL_8PT_NORMAL)));
                  pCell.setHorizontalAlignment(com.lowagie.text.Element.ALIGN_CENTER);
                  pCell.setBorder(0);
                  curteam.addCell(pCell);
                }
          
                // Last column contains the overall scaled score
                pCell = new PdfPCell((Double.isNaN(totalScore) ? new Phrase(
                                                                            "No Score", ARIAL_8PT_NORMAL_RED) : new Phrase(SCORE_FORMAT
                                                                                                                           .format(totalScore), ARIAL_8PT_NORMAL)));
                pCell.setHorizontalAlignment(com.lowagie.text.Element.ALIGN_CENTER);
                pCell.setBorder(0);
                curteam.addCell(pCell);

                // This is an empty row in the team table that is added to put a
                // horizontal rule under the team's score in the display
                blankCell = new PdfPCell();
                blankCell.setBorder(0);
                blankCell.setBorderWidthBottom(0.5f);
                blankCell.setBorderColorBottom(Color.GRAY);
                blankCell.setColspan(4 + nonZeroWeights);
                curteam.addCell(blankCell);

                // Create a new cell and add it to the division table - this cell will
                // contain the entire team table we just built above
                PdfPCell curteamCell = new PdfPCell(curteam);
                curteamCell.setBorder(0);
                curteamCell.setColspan(relativeWidths.length);
                divTable.addCell(curteamCell);
            }

            teamsRS.close();

            // Add the division table to the document
            pdfDoc.add(divTable);
        
            // If there is another division to process, start it on a new page
            if(divisionIter.hasNext()) {
              pdfDoc.newPage();
            }

      } //end while(divisionIter.next())

      pdfDoc.close();
    } catch(final ParseException pe) {
      throw new RuntimeException("Error parsing category weight!", pe);
    } catch(final DocumentException de) {
      throw new RuntimeException("Error creating PDF document!", de);
    } finally {
      Utilities.closeResultSet(rawScoreRS);
      Utilities.closeResultSet(teamsRS);
      
      Utilities.closeStatement(stmt);
      Utilities.closeStatement(teamsStmt);
    }
  }

  private static final NumberFormat SCORE_FORMAT = NumberFormat.getInstance();
  static {
    SCORE_FORMAT.setMaximumFractionDigits(2);
    SCORE_FORMAT.setMinimumFractionDigits(2);
  }    

}