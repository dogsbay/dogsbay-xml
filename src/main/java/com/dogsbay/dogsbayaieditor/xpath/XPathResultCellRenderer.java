/*
 * Copyright (C) 2002-2026 DogsBay Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dogsbay.dogsbayaieditor.xpath;

import com.dogsbay.dogsbayaieditor.IconFactory;
import com.dogsbay.dogsbayaieditor.URLUtilities;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.File;

/**
 * Renders XPath result list cells: file group headers and individual result items.
 */
public class XPathResultCellRenderer extends JPanel implements ListCellRenderer<Object> {
    private static final Border NO_FOCUS_BORDER = new EmptyBorder(1, 1, 1, 1);
    private static final Color GRAY_TEXT = new Color(128, 128, 128);

    private JLabel groupIcon;
    private JLabel groupPath;
    private JLabel groupCount;
    private JPanel groupPanel;

    private JLabel resultType;
    private JLabel resultName;
    private JLabel resultValue;
    private JPanel resultPanel;

    private File searchRoot;

    public XPathResultCellRenderer() {
        super(new BorderLayout());
        createGroupPanel();
        createResultPanel();
    }

    private void createGroupPanel() {
        groupPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        groupPanel.setOpaque(false);

        groupIcon = new JLabel();

        groupPath = new JLabel();
        groupPath.setFont(groupPath.getFont().deriveFont(Font.BOLD));

        groupCount = new JLabel();
        groupCount.setFont(groupCount.getFont().deriveFont(Font.PLAIN));
        groupCount.setForeground(GRAY_TEXT);

        groupPanel.add(groupIcon);
        groupPanel.add(groupPath);
        groupPanel.add(groupCount);
    }

    private void createResultPanel() {
        resultPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        resultPanel.setOpaque(false);

        JLabel indent = new JLabel("    ");
        resultPanel.add(indent);

        resultType = new JLabel();
        resultType.setFont(resultType.getFont().deriveFont(Font.PLAIN));
        resultType.setForeground(GRAY_TEXT);
        resultType.setBorder(new EmptyBorder(0, 2, 0, 4));

        resultName = new JLabel();
        resultName.setFont(resultName.getFont().deriveFont(Font.BOLD));

        resultValue = new JLabel();
        resultValue.setFont(resultValue.getFont().deriveFont(Font.PLAIN));
        resultValue.setForeground(GRAY_TEXT);

        resultPanel.add(resultType);
        resultPanel.add(resultName);
        resultPanel.add(resultValue);
    }

    public void setSearchRoot(File searchRoot) {
        this.searchRoot = searchRoot;
    }

    @Override
    public Component getListCellRendererComponent(JList<? extends Object> list, Object value,
                                                 int index, boolean isSelected, boolean cellHasFocus) {
        removeAll();

        if (value instanceof XPathResultGroup) {
            renderGroup((XPathResultGroup) value);
        } else if (value instanceof XPathResultGroup.XPathResultItem) {
            renderResult((XPathResultGroup.XPathResultItem) value);
        }

        if (isSelected) {
            setBackground(list.getSelectionBackground());
            applySelectionColors(list.getSelectionForeground());
        } else {
            setBackground(list.getBackground());
            applyNormalColors(list.getForeground());
        }

        setEnabled(list.isEnabled());
        setBorder(cellHasFocus ? UIManager.getBorder("List.focusCellHighlightBorder") : NO_FOCUS_BORDER);

        return this;
    }

    private void renderGroup(XPathResultGroup group) {
        add(groupPanel, BorderLayout.CENTER);

        File file = group.getFile();
        String fileName = file.getName();
        String extension = URLUtilities.getExtension(fileName);
        if (extension == null) {
            extension = "";
        }

        groupIcon.setIcon(IconFactory.getIconForExtension(extension));
        groupPath.setText(group.getRelativePath(searchRoot));

        int count = group.getResultCount();
        groupCount.setText("[" + count + (count == 1 ? " result]" : " results]"));

        setToolTipText(file.getAbsolutePath());
    }

    private void renderResult(XPathResultGroup.XPathResultItem item) {
        add(resultPanel, BorderLayout.CENTER);

        resultType.setText("[" + item.getNodeType() + "]");
        resultName.setText(item.getNodeName());

        String value = item.getNodeValue();
        if (value != null && !value.isEmpty()) {
            if (value.length() > 80) {
                value = value.substring(0, 77) + "...";
            }
            resultValue.setText("  = " + value);
        } else {
            resultValue.setText("");
        }

        setToolTipText("Line " + item.getLineNumber() + ": " + item.getXPath());
    }

    private void applySelectionColors(Color foreground) {
        groupPath.setForeground(foreground);
        groupCount.setForeground(foreground);
        resultType.setForeground(foreground);
        resultName.setForeground(foreground);
        resultValue.setForeground(foreground);
    }

    private void applyNormalColors(Color foreground) {
        groupPath.setForeground(foreground);
        groupCount.setForeground(GRAY_TEXT);
        resultType.setForeground(GRAY_TEXT);
        resultName.setForeground(foreground);
        resultValue.setForeground(GRAY_TEXT);
    }
}
