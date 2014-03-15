package org.worldcubeassociation.ui;

import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;

import javax.swing.*;

import org.worldcubeassociation.WorkbookAssistantEnv;
import org.worldcubeassociation.workbook.scrambles.DecodedScrambleFile;
import org.worldcubeassociation.workbook.scrambles.Scrambles;

/**
 * @author Lars Vandenbergh
 */
public class ScramblesFilesField extends JList implements PropertyChangeListener {

    private WorkbookAssistantEnv fEnv;
    private JScrollPane fScrollPane;

    public ScramblesFilesField(WorkbookAssistantEnv aEnv, JScrollPane aScrollPane) {
        super();
        fEnv = aEnv;
        fScrollPane = aScrollPane;
        fEnv.addPropertyChangeListener(this);

        updateContent();
        updateEnabledState();
    }

    private void updateContent() {
        DefaultListModel listModel = new DefaultListModel();

        Scrambles scrambles = fEnv.getScrambles();
        if (scrambles != null) {
            for (DecodedScrambleFile decodedScrambleFile : scrambles.getDecodedScrambleFiles()) {
                listModel.addElement(decodedScrambleFile.getScrambleFile());
            }
        }

        setModel(listModel);
        setVisibleRowCount(listModel.size() == 0 ? 1 : listModel.size());

        Container parent = fScrollPane.getParent();
        if (parent != null) {
            // You can't call revalidate on a Container in java 6. It was introduced in
            // java 7:
            //  http://docs.oracle.com/javase/7/docs/api/java/awt/Component.html#revalidate()
            //parent.revalidate();
            parent.repaint();
        }
    }

    private void updateEnabledState() {
        boolean enabled = fEnv.getMatchedWorkbook() != null;
        setEnabled(enabled);
        setOpaque(enabled);
    }

    @Override
    public void propertyChange(PropertyChangeEvent aPropertyChangeEvent) {
        if (WorkbookAssistantEnv.SCRAMBLES.equals(aPropertyChangeEvent.getPropertyName())) {
            updateContent();
        }
        else if (WorkbookAssistantEnv.MATCHED_WORKBOOK.equals(aPropertyChangeEvent.getPropertyName())) {
            updateEnabledState();
        }
    }

}
