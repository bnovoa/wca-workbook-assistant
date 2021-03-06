package org.worldcubeassociation.ui.table;

import org.worldcubeassociation.WorkbookAssistantEnv;
import org.worldcubeassociation.workbook.MatchedSheet;
import org.worldcubeassociation.workbook.Round;
import org.worldcubeassociation.workbook.WorkbookValidator;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author Lars Vandenbergh
 */
public class RoundCellEditor extends DefaultCellEditor {

    private MatchedSheet fMatchedSheet;
    private WorkbookAssistantEnv fEnv;

    public RoundCellEditor(WorkbookAssistantEnv aEnv) {
        super(createComboBox());
        fEnv = aEnv;
        setClickCountToStart(2);
    }

    private static JComboBox createComboBox() {
        List<Round> rounds = new ArrayList<Round>(Arrays.asList(Round.values()));
        rounds.add(0, null);
        return new JComboBox(rounds.toArray());
    }

    @Override
    public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected,
                                                 int row, int column) {
        fMatchedSheet = (MatchedSheet) value;
        getComponent().setFont(getComponent().getFont().deriveFont(fEnv.getFontSize()));
        return super.getTableCellEditorComponent(table, fMatchedSheet.getRound(), isSelected, row, column);
    }


    @Override
    public boolean stopCellEditing() {
        Round newValue = (Round) getCellEditorValue();
        if (newValue != fMatchedSheet.getRound()) {
            fMatchedSheet.setRound(newValue);
            WorkbookValidator.validateSheetsForEvent(fEnv.getMatchedWorkbook(), fMatchedSheet.getEvent(), fEnv.getDatabase(), fEnv.getScrambles());
            fEnv.fireSheetChanged(fMatchedSheet);
        }
        return super.stopCellEditing();
    }

    @Override
    public void cancelCellEditing() {
        super.cancelCellEditing();
    }

}
