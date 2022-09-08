/*
 * Copyright (C) 2020 European Spallation Source ERIC.
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 *
 */

package org.phoebus.applications.saveandrestore.ui.configuration;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.util.Callback;
import org.phoebus.applications.saveandrestore.Messages;
import org.phoebus.applications.saveandrestore.SaveAndRestoreApplication;
import org.phoebus.applications.saveandrestore.model.ConfigPv;
import org.phoebus.applications.saveandrestore.model.Configuration;
import org.phoebus.applications.saveandrestore.model.ConfigurationData;
import org.phoebus.applications.saveandrestore.model.Node;
import org.phoebus.applications.saveandrestore.model.NodeType;
import org.phoebus.applications.saveandrestore.ui.SaveAndRestoreController;
import org.phoebus.applications.saveandrestore.ui.SaveAndRestoreService;
import org.phoebus.core.types.ProcessVariable;
import org.phoebus.framework.selection.SelectionService;
import org.phoebus.ui.application.ContextMenuHelper;
import org.phoebus.ui.dialog.ExceptionDetailsErrorDialog;
import org.phoebus.ui.javafx.ImageCache;

import javax.swing.border.Border;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class ConfigurationController {

    @FXML
    private BorderPane root;

    @FXML
    private TableColumn<ConfigPv, String> pvNameColumn;

    @FXML
    private TableView<ConfigPv> pvTable;

    @FXML
    private TextArea commentTextArea;

    @FXML
    private Button saveButton;

    @FXML
    private TextField pvNameField;

    @FXML
    private TextField readbackPvNameField;

    @FXML
    private Button addPvButton;

    @FXML
    private CheckBox readOnlyCheckBox;

    @FXML
    private final SimpleStringProperty pvNameProperty = new SimpleStringProperty("");
    @FXML
    private final SimpleStringProperty readbackPvNameProperty = new SimpleStringProperty("");

    @FXML
    private final SimpleBooleanProperty readOnlyProperty = new SimpleBooleanProperty(false);

    @FXML
    private TextField saveSetNameField;

    @FXML
    private Label saveSetDateField;
    @FXML
    private Label createdByField;

    private SaveAndRestoreService saveAndRestoreService;

    private static final Executor UI_EXECUTOR = Platform::runLater;

    private final SimpleBooleanProperty dirty = new SimpleBooleanProperty(false);

    private final ObservableList<ConfigPv> saveSetEntries = FXCollections.observableArrayList();

    private final SimpleBooleanProperty selectionEmpty = new SimpleBooleanProperty(false);
    private final SimpleBooleanProperty singelSelection = new SimpleBooleanProperty(false);
    private final SimpleStringProperty saveSetCommentProperty = new SimpleStringProperty();
    private final SimpleStringProperty saveSetNameProperty = new SimpleStringProperty();
    private Node configurationNodeParent;


    private Configuration configuration;

    private TableView.TableViewSelectionModel<ConfigPv> defaultSelectionModel;

    private final Logger logger = Logger.getLogger(ConfigurationController.class.getName());

    private SimpleStringProperty tabTitleProperty;

    public ConfigurationController(SimpleStringProperty tabTitleProperty){
        this.tabTitleProperty = tabTitleProperty;
    }

    @FXML
    public void initialize() {

        saveAndRestoreService = SaveAndRestoreService.getInstance();

        pvTable.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        pvTable.getSelectionModel().selectedItemProperty().addListener((obs, ov, nv) -> selectionEmpty.set(nv == null));

        pvTable.getSelectionModel().getSelectedItems().addListener((ListChangeListener<ConfigPv>) c -> singelSelection.set(c.getList().size() == 1));

        defaultSelectionModel = pvTable.getSelectionModel();

        ContextMenu pvNameContextMenu = new ContextMenu();

        MenuItem deleteMenuItem = new MenuItem(Messages.menuItemDeleteSelectedPVs,
                new ImageView(ImageCache.getImage(SaveAndRestoreController.class, "/icons/delete.png")));
        deleteMenuItem.setOnAction(ae -> {
            saveSetEntries.removeAll(pvTable.getSelectionModel().getSelectedItems());
            pvTable.refresh();
        });

        deleteMenuItem.disableProperty().bind(Bindings.createBooleanBinding(() -> pvTable.getSelectionModel().getSelectedItems().isEmpty(),
                pvTable.getSelectionModel().getSelectedItems()));

        pvNameColumn.setEditable(true);
        pvNameColumn.setCellValueFactory(new PropertyValueFactory<>("pvName"));

        pvNameColumn.setCellFactory(new Callback<>() {
            @Override
            public TableCell call(TableColumn param) {
                final TableCell<ConfigPv, String> cell = new TableCell<>() {
                    @Override
                    public void updateItem(String item, boolean empty) {
                        super.updateItem(item, empty);
                        selectionEmpty.set(empty);
                        if (empty) {
                            setText(null);
                        } else {
                            if (isEditing()) {
                                setText(null);
                            } else {
                                setText(getItem());
                                setGraphic(null);
                            }
                        }
                    }
                };
                cell.setOnContextMenuRequested(event -> {
                    pvNameContextMenu.hide();
                    pvNameContextMenu.getItems().clear();
                    pvNameContextMenu.getItems().addAll(deleteMenuItem);
                    pvNameContextMenu.getItems().add(new SeparatorMenuItem());
                    ObservableList<ConfigPv> selectedPVs = pvTable.getSelectionModel().getSelectedItems();
                    if(!selectedPVs.isEmpty()){
                        List<ProcessVariable> selectedPVList = selectedPVs.stream()
                                .map(tableEntry -> new ProcessVariable(tableEntry.getPvName()))
                                .collect(Collectors.toList());
                        SelectionService.getInstance().setSelection(SaveAndRestoreApplication.NAME, selectedPVList);
                        ContextMenuHelper.addSupportedEntries(cell, pvNameContextMenu);
                    }
                    pvNameContextMenu.show(cell, event.getScreenX(), event.getScreenY());
                });
                cell.setContextMenu(pvNameContextMenu);

                return cell;
            }
        });

        pvNameField.textProperty().bindBidirectional(pvNameProperty);
        readbackPvNameField.textProperty().bindBidirectional(readbackPvNameProperty);
        saveSetNameField.textProperty().bindBidirectional(saveSetNameProperty);
        commentTextArea.textProperty().bindBidirectional(saveSetCommentProperty);

        saveSetEntries.addListener((ListChangeListener<ConfigPv>) change -> {
            while (change.next()) {
                if (change.wasAdded() || change.wasRemoved()) {
                    FXCollections.sort(saveSetEntries);
                    dirty.set(true);
                }
            }
        });

        saveSetNameProperty.addListener((observableValue, oldValue, newValue) -> {
            dirty.set(!newValue.equals(configuration.getConfigurationNode().getName()));
        });

        saveSetCommentProperty.addListener((observable, oldValue, newValue) -> {
            dirty.set(!newValue.equals(configuration.getConfigurationData().getDescription()));
        });

        saveButton.disableProperty().bind(Bindings.createBooleanBinding(() -> dirty.not().get() ||
                        saveSetCommentProperty.isEmpty().get() ||
                        saveSetNameProperty.isEmpty().get(),
                dirty, saveSetCommentProperty, saveSetNameProperty));

        addPvButton.disableProperty().bind(pvNameField.textProperty().isEmpty());

        readOnlyCheckBox.selectedProperty().bindBidirectional(readOnlyProperty);
    }

    @FXML
    public void saveConfiguration() {

        UI_EXECUTOR.execute(() -> {
            try {
                configuration.getConfigurationData().setDescription(saveSetCommentProperty.getValue());
                configuration.getConfigurationData().setPvList(saveSetEntries);
                configuration.getConfigurationNode().setName(saveSetNameProperty.get());
                if (configuration.getConfigurationNode().getUniqueId() == null) { // New configuration
                    configuration = saveAndRestoreService.createConfiguration(configurationNodeParent,
                            configuration);
                } else {
                    configuration = saveAndRestoreService.updateConfiguration(configuration);
                }
                tabTitleProperty.set(configuration.getConfigurationNode().getName());
                dirty.set(false);

                //loadedConfig.putProperty(DESCRIPTION_PROPERTY, saveSetCommentProperty.getValue());
                //loadedConfig = saveAndRestoreService.updateSaveSet(loadedConfig, saveSetEntries);

                //loadConfiguration(configurationNode);
            } catch (Exception e1) {
                ExceptionDetailsErrorDialog.openError(pvTable,
                        Messages.errorActionFailed,
                        Messages.errorCreateSaveSetFailed,
                        e1);
            }
        });
    }

    @FXML
    public void addPv() {

        UI_EXECUTOR.execute(() -> {
            // Process a list of space or semi colon separated pvs
            String[] pvNames = pvNameProperty.get().trim().split("[\\s;]+");
            String[] readbackPvNames = readbackPvNameProperty.get().trim().split("[\\s;]+");

            ArrayList<ConfigPv> configPVs = new ArrayList<>();
            for (int i = 0; i < pvNames.length; i++) {
                // Disallow duplicate PV names as in a restore operation this would mean that a PV is written
                // multiple times, possibly with different values.
                String pvName = pvNames[i].trim();
                if(saveSetEntries.stream().anyMatch(s -> s.getPvName().equals(pvName))){
                    continue;
                }
                String readbackPV = i >= readbackPvNames.length ? null : readbackPvNames[i] == null || readbackPvNames[i].isEmpty() ? null : readbackPvNames[i].trim();
                ConfigPv configPV = ConfigPv.builder()
                        .pvName(pvName)
                        .readOnly(readOnlyProperty.get())
                        .readbackPvName(readbackPV)
                        .build();
                configPVs.add(configPV);
            }
            saveSetEntries.addAll(configPVs);
            resetAddPv();
        });

    }

    private void resetAddPv() {
        pvNameProperty.set("");
        readOnlyProperty.set(false);
        readbackPvNameProperty.set("");

        pvNameField.requestFocus();
    }

    public void newConfiguration(Node parentNode) {
        configurationNodeParent = parentNode;
        configuration = new Configuration();
        configuration.setConfigurationNode(Node.builder().nodeType(NodeType.CONFIGURATION).build());
        configuration.setConfigurationData(new ConfigurationData());
        createdByField.textProperty().set(null);
        saveSetDateField.textProperty().set(null);
        dirty.set(false);
    }

    public void loadConfiguration(Node configurationNode) {
        configuration = new Configuration();
        configuration.setConfigurationNode(configurationNode);
        try {
            configuration.setConfigurationData(saveAndRestoreService.getConfiguration(configurationNode.getUniqueId()));
        } catch (Exception e) {
            ExceptionDetailsErrorDialog.openError(root, "Error", "Unable to retrieve configuration data", e);
            return;
        }
        UI_EXECUTOR.execute(() -> {
            try {
                saveSetNameProperty.set(configurationNode.getName());
                createdByField.textProperty().set(configurationNode.getUserName());
                saveSetDateField.textProperty().set(configurationNode.getCreated().toString());
                tabTitleProperty.set(configurationNode.getName());
                loadConfiguration();
            } catch (Exception e) {
                logger.log(Level.WARNING, "Unable to load existing save set");
            }
        });
    }

    private void loadConfiguration() {
        UI_EXECUTOR.execute(() -> {
            try {
                saveSetCommentProperty.set(configuration.getConfigurationData().getDescription());
                Collections.sort(configuration.getConfigurationData().getPvList());
                saveSetEntries.setAll(configuration.getConfigurationData().getPvList());
                pvTable.setItems(saveSetEntries);
                dirty.set(false);
            } catch (Exception e) {
                logger.log(Level.WARNING, "Unable to load existing save set");
            }
        });
    }

    public boolean handleSaveSetTabClosed() {
        if (dirty.get()) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Close tab?");
            alert.setContentText("Save set has been modified but is not saved. Do you wish to continue?");
            Optional<ButtonType> result = alert.showAndWait();
            return result.isPresent() && result.get().equals(ButtonType.OK);
        }
        return true;
    }
}
