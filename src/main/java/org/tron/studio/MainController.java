package org.tron.studio;

import javafx.event.EventHandler;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.*;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.ScrollEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.tron.studio.filesystem.SolidityFileUtil;
import org.tron.studio.utils.SolidityHighlight;
import org.tron.studio.utils.AutoCompletion;
import org.tron.studio.utils.FormatCode;

import javax.annotation.PostConstruct;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

import org.fxmisc.flowless.VirtualizedScrollPane;
import javafx.application.Platform;


@Slf4j
public class MainController {
    public TabPane rightContentTabPane;
    public TabPane codeAreaTabPane;

    public Tab defaultCodeAreaTab;
    public CodeArea defaultCodeArea;

    int scrollPos = 0;

    private static List<Tab> allTabs = new ArrayList<>();

    @PostConstruct
    public void initialize() throws IOException {
        List<File> files = SolidityFileUtil.getFileNameList();
        File defaultContractFile = files.get(0);
        ShareData.currentContractFileName.set(defaultContractFile.getName());

        codeAreaTabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);

        StringBuilder builder = new StringBuilder();
        try {
            Files.lines(Paths.get(defaultContractFile.getAbsolutePath())).forEach(line -> {
                builder.append(line).append(System.getProperty("line.separator"));
            });
        } catch (IOException e) {
            e.printStackTrace();
        }

        defaultCodeAreaTab.setText(defaultContractFile.getName());
        //Just not allow to close the default tab
        defaultCodeAreaTab.setClosable(false);
        allTabs.add(defaultCodeAreaTab);

        defaultCodeArea = (CodeArea) defaultCodeAreaTab.getContent();
        new SolidityHighlight(defaultCodeArea).highlight();
        AutoCompletion autocomp = new AutoCompletion(defaultCodeArea);
        autocomp.autoComplete(defaultCodeArea);

        VirtualizedScrollPane scrollPane = new VirtualizedScrollPane<>(defaultCodeArea);
        defaultCodeAreaTab.setContent(scrollPane);

        setScrollStatus(defaultCodeArea, scrollPane);

        defaultCodeArea.replaceText(0, 0, builder.toString());

        new FormatCode(defaultCodeArea);
        defaultCodeArea.setParagraphGraphicFactory(LineNumberFactory.get(defaultCodeArea));
        Platform.runLater(() -> defaultCodeArea.selectRange(0, 0));

        ShareData.currentContractTab = defaultCodeAreaTab;
        ShareData.allContractFileName.add(defaultContractFile.getName());

        codeAreaTabPane.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue == null)
            {
                return;
            }
            ShareData.currentContractTab = newValue;
            ShareData.currentContractFileName.set(newValue.getText());
        });

        ShareData.deleteContract.addListener((observable, oldValue, currentContractName) -> {
            for (Tab tab : codeAreaTabPane.getTabs()) {
                if (StringUtils.equals(tab.getText(), currentContractName)) {
                    codeAreaTabPane.getTabs().remove(tab);
                    break;
                }
            }
        });

        ShareData.currentContractFileName.addListener((observable, oldValue, currentContractName) -> {
            boolean alreadyOpen = false;
            for (Tab tab : codeAreaTabPane.getTabs()) {
                if (StringUtils.equals(tab.getText(), currentContractName)) {
                    System.out.println(tab.getText());
                    codeAreaTabPane.getSelectionModel().select(tab);
                    alreadyOpen = true;
                    break;
                }
            }
            if (!alreadyOpen) {
                createTabForFileSystemFile(currentContractName);
            }
        });

        ShareData.newContractFileName.addListener((observable, oldValue, newValue) -> {
            createTabForFileSystemFile(newValue);
        });

        ShareData.openContractFileName.addListener((observable, oldValue, newValue) ->{
            String filePath = ShareData.openContractFileName.get();
            File newFile = new File(filePath);
            System.out.println(filePath);
            Tab newTab = setTab(newFile);
            newTab.setClosable(true);
//            ShareData.currentContractName.set(filePath);
//            ShareData.allContractFileName.add(filePath);
            codeAreaTabPane.getSelectionModel().select(newTab);
        });

        ShareData.debugTransactionAction.addListener((observable, oldValue, newValue) -> {
            rightContentTabPane.getSelectionModel().selectLast();
        });
    }

    private Tab setTab(File file) {
        Tab codeTab = new Tab();
        logger.info("set codeTab");

        codeTab.setOnCloseRequest( e -> {
            closeTab((Tab)e.getTarget());
        });

        allTabs.add(codeTab);

        CodeArea codeArea = new CodeArea();
        // Print new file in codearea
        StringBuilder builder = new StringBuilder();
        try {
            Files.lines(Paths.get(file.getAbsolutePath())).forEach(line -> {
                builder.append(line).append(System.getProperty("line.separator"));
            });
        } catch (IOException e) {
            e.printStackTrace();
        }

        AutoCompletion autoCompletion = new AutoCompletion(codeArea);
        autoCompletion.autoComplete(codeArea);

        codeTab.setText(file.getPath());
        //Just not allow to close the default tab
        codeTab.setClosable(true);

        VirtualizedScrollPane scrollPane = new VirtualizedScrollPane<>(codeArea);
        codeTab.setContent(scrollPane);

        setScrollStatus(codeArea, scrollPane);

        Platform.runLater(() -> codeArea.selectRange(0, 0));

        codeAreaTabPane.getTabs().add(codeTab);

        new SolidityHighlight(codeArea).highlight();
        codeArea.replaceText(0, 0, builder.toString());
        codeArea.setParagraphGraphicFactory(LineNumberFactory.get(codeArea));

        new FormatCode(codeArea);

        ShareData.allContractFileName.add(file.getPath());
        ShareData.currentContractName.set(file.getPath());
        ShareData.currentContractTab = codeTab;

        return codeTab;
    }

    private void createTabForFileSystemFile(String fileName) {
        try {
            Tab codeTab = new Tab();
            codeTab.setText(fileName);
            codeTab.setClosable(true);

            codeTab.setOnCloseRequest( e -> {
                closeTab((Tab)e.getTarget());
            });
            allTabs.add(codeTab);

            CodeArea codeArea = FXMLLoader.load(getClass().getResource("ui/code_area.fxml"));

            AutoCompletion autoCompletion = new AutoCompletion(codeArea);
            autoCompletion.autoComplete(codeArea);

            VirtualizedScrollPane scrollPane = new VirtualizedScrollPane<>(codeArea);
            codeTab.setContent(scrollPane);
            Platform.runLater(() -> codeArea.selectRange(0, 0));

            setScrollStatus(codeArea, scrollPane);

            codeAreaTabPane.getTabs().add(codeTab);

            String sourceCode = SolidityFileUtil.getSourceCode(fileName);

            new SolidityHighlight(codeArea).highlight();
            codeArea.insertText(0, sourceCode);
            codeArea.setParagraphGraphicFactory(LineNumberFactory.get(codeArea));

            new FormatCode(codeArea);

            codeAreaTabPane.getSelectionModel().select(codeTab);
            ShareData.currentContractTab = codeTab;
        } catch (IOException e) {
            logger.error(e.getMessage());
        }
    }

    private void setScrollStatus(CodeArea codeArea, VirtualizedScrollPane scrollPane)
    {
        codeArea.addEventFilter(KeyEvent.KEY_PRESSED, new EventHandler<KeyEvent>() {
            @Override
            public void handle(KeyEvent event) {
                ShareData.isScrolling = false;
            }
        });

        scrollPane.addEventFilter(ScrollEvent.ANY, new EventHandler<ScrollEvent>() {
            @Override
            public void handle(ScrollEvent event) {
                ShareData.isScrolling = true;
            }
        });
    }

    private void closeTab(Tab currTab)
    {
        ShareData.currentContractFileName.set(currTab.getText());
        ShareData.currentContractTab = currTab;

        // Update contract file name and current contract file name
        int currFileIndex = 0;
        int filesNum = ShareData.allContractFileName.getSize();
        for (int i = 0; i < filesNum; i++)
        {
            if (ShareData.currentContractFileName.equals((String)ShareData.allContractFileName.toArray()[i]))
            {
                currFileIndex = i;
                break;
            }
        }

        ShareData.allContractFileName.remove(ShareData.currentContractFileName.get());

        if (currFileIndex > 0)
        {
            currFileIndex -= 1;
        }
        ShareData.currentContractFileName.set((String)ShareData.allContractFileName.toArray()[currFileIndex]);

        // Update tabs
        Tab preTab = ShareData.currentContractTab;
        for (Tab tab: allTabs)
        {
            if (tab.equals(ShareData.currentContractTab))
            {
                allTabs.remove(ShareData.currentContractTab);
                break;
            }
            preTab = tab;
        }
        ShareData.currentContractTab = preTab;
    }


}
