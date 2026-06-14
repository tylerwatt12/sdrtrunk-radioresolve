/*
 * *****************************************************************************
 * Copyright (C) 2026 Dennis Sheirer
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 * ****************************************************************************
 */

package io.github.dsheirer.gui.playlist.streaming;

import io.github.dsheirer.audio.broadcast.BroadcastServerType;
import io.github.dsheirer.audio.broadcast.radioresolve.RadioResolveBroadcaster;
import io.github.dsheirer.audio.broadcast.radioresolve.RadioResolveConfiguration;
import io.github.dsheirer.gui.control.IntegerTextField;
import io.github.dsheirer.playlist.PlaylistManager;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;

/**
 * RadioResolve completed call upload configuration editor.
 */
public class RadioResolveEditor extends AbstractBroadcastEditor<RadioResolveConfiguration>
{
    private PasswordField mApiKeyTextField;
    private TextField mHostTextField;
    private TextField mNodeNameTextField;
    private TextField mNodeTimezoneTextField;
    private IntegerTextField mMaxAgeTextField;
    private Button mTestButton;
    private GridPane mEditorPane;

    /**
     * Constructs an instance.
     * @param playlistManager for accessing the broadcast model
     */
    public RadioResolveEditor(PlaylistManager playlistManager)
    {
        super(playlistManager);
    }

    @Override
    public void setItem(RadioResolveConfiguration item)
    {
        super.setItem(item);

        getApiKeyTextField().setDisable(item == null);
        getHostTextField().setDisable(item == null);
        getNodeNameTextField().setDisable(item == null);
        getNodeTimezoneTextField().setDisable(item == null);
        getMaxAgeTextField().setDisable(item == null);
        getTestButton().setDisable(item == null);

        if(item != null)
        {
            getApiKeyTextField().setText(item.getApiKey());
            getHostTextField().setText(item.getHost());
            getNodeNameTextField().setText(item.getNodeName());
            getNodeTimezoneTextField().setText(item.getNodeTimezone());
            getMaxAgeTextField().set((int)(item.getMaximumRecordingAge() / 1000));
        }
        else
        {
            getApiKeyTextField().setText(null);
            getHostTextField().setText(null);
            getNodeNameTextField().setText(null);
            getNodeTimezoneTextField().setText(null);
            getMaxAgeTextField().set(0);
        }

        modifiedProperty().set(false);
    }

    @Override
    public void dispose()
    {
    }

    @Override
    public void save()
    {
        if(getItem() != null)
        {
            getItem().setApiKey(getApiKeyTextField().getText());
            getItem().setHost(getHostTextField().getText());
            getItem().setNodeName(getNodeNameTextField().getText());
            getItem().setNodeTimezone(getNodeTimezoneTextField().getText());
            getItem().setMaximumRecordingAge(getMaxAgeSeconds() * 1000L);
        }

        super.save();
    }

    @Override
    public BroadcastServerType getBroadcastServerType()
    {
        return BroadcastServerType.RADIORESOLVE;
    }

    @Override
    protected GridPane getEditorPane()
    {
        if(mEditorPane == null)
        {
            mEditorPane = new GridPane();
            mEditorPane.setPadding(new Insets(10, 5, 10,10));
            mEditorPane.setVgap(10);
            mEditorPane.setHgap(5);

            int row = 0;

            Label formatLabel = new Label("Format");
            GridPane.setHalignment(formatLabel, HPos.RIGHT);
            GridPane.setConstraints(formatLabel, 0, row);
            mEditorPane.getChildren().add(formatLabel);

            GridPane.setConstraints(getFormatField(), 1, row);
            mEditorPane.getChildren().add(getFormatField());

            Label enabledLabel = new Label("Enabled");
            GridPane.setHalignment(enabledLabel, HPos.RIGHT);
            GridPane.setConstraints(enabledLabel, 2, row);
            mEditorPane.getChildren().add(enabledLabel);

            GridPane.setConstraints(getEnabledSwitch(), 3, row);
            mEditorPane.getChildren().add(getEnabledSwitch());

            Label nameLabel = new Label("Name");
            GridPane.setHalignment(nameLabel, HPos.RIGHT);
            GridPane.setConstraints(nameLabel, 0, ++row);
            mEditorPane.getChildren().add(nameLabel);

            GridPane.setConstraints(getNameTextField(), 1, row);
            mEditorPane.getChildren().add(getNameTextField());

            Label apiKeyLabel = new Label("API Key");
            GridPane.setHalignment(apiKeyLabel, HPos.RIGHT);
            GridPane.setConstraints(apiKeyLabel, 0, ++row);
            mEditorPane.getChildren().add(apiKeyLabel);

            GridPane.setConstraints(getApiKeyTextField(), 1, row);
            mEditorPane.getChildren().add(getApiKeyTextField());

            Label hostLabel = new Label("RadioResolve URL");
            GridPane.setHalignment(hostLabel, HPos.RIGHT);
            GridPane.setConstraints(hostLabel, 0, ++row);
            mEditorPane.getChildren().add(hostLabel);

            GridPane.setConstraints(getHostTextField(), 1, row);
            mEditorPane.getChildren().add(getHostTextField());

            Label nodeNameLabel = new Label("Node Name");
            GridPane.setHalignment(nodeNameLabel, HPos.RIGHT);
            GridPane.setConstraints(nodeNameLabel, 0, ++row);
            mEditorPane.getChildren().add(nodeNameLabel);

            GridPane.setConstraints(getNodeNameTextField(), 1, row);
            mEditorPane.getChildren().add(getNodeNameTextField());

            Label timezoneLabel = new Label("Node Timezone");
            GridPane.setHalignment(timezoneLabel, HPos.RIGHT);
            GridPane.setConstraints(timezoneLabel, 0, ++row);
            mEditorPane.getChildren().add(timezoneLabel);

            GridPane.setConstraints(getNodeTimezoneTextField(), 1, row);
            mEditorPane.getChildren().add(getNodeTimezoneTextField());

            Label maxAgeLabel = new Label("Max Recording Age (seconds)");
            GridPane.setHalignment(maxAgeLabel, HPos.RIGHT);
            GridPane.setConstraints(maxAgeLabel, 0, ++row);
            mEditorPane.getChildren().add(maxAgeLabel);

            GridPane.setConstraints(getMaxAgeTextField(), 1, row);
            mEditorPane.getChildren().add(getMaxAgeTextField());

            GridPane.setConstraints(getTestButton(), 1, ++row);
            mEditorPane.getChildren().add(getTestButton());
        }

        return mEditorPane;
    }

    private PasswordField getApiKeyTextField()
    {
        if(mApiKeyTextField == null)
        {
            mApiKeyTextField = new PasswordField();
            mApiKeyTextField.setDisable(true);
            mApiKeyTextField.textProperty().addListener(mEditorModificationListener);
        }

        return mApiKeyTextField;
    }

    private TextField getHostTextField()
    {
        if(mHostTextField == null)
        {
            mHostTextField = new TextField();
            mHostTextField.setDisable(true);
            mHostTextField.textProperty().addListener(mEditorModificationListener);
        }

        return mHostTextField;
    }

    private TextField getNodeNameTextField()
    {
        if(mNodeNameTextField == null)
        {
            mNodeNameTextField = new TextField();
            mNodeNameTextField.setDisable(true);
            mNodeNameTextField.textProperty().addListener(mEditorModificationListener);
        }

        return mNodeNameTextField;
    }

    private TextField getNodeTimezoneTextField()
    {
        if(mNodeTimezoneTextField == null)
        {
            mNodeTimezoneTextField = new TextField();
            mNodeTimezoneTextField.setDisable(true);
            mNodeTimezoneTextField.textProperty().addListener(mEditorModificationListener);
        }

        return mNodeTimezoneTextField;
    }

    private IntegerTextField getMaxAgeTextField()
    {
        if(mMaxAgeTextField == null)
        {
            mMaxAgeTextField = new IntegerTextField();
            mMaxAgeTextField.setDisable(true);
            mMaxAgeTextField.textProperty().addListener(mEditorModificationListener);
        }

        return mMaxAgeTextField;
    }

    private Button getTestButton()
    {
        if(mTestButton == null)
        {
            mTestButton = new Button("Test Connection");
            mTestButton.setDisable(true);
            mTestButton.setOnAction(event -> testConnection());
        }

        return mTestButton;
    }

    private int getMaxAgeSeconds()
    {
        Integer seconds = getMaxAgeTextField().get();
        return seconds != null ? seconds : 0;
    }

    private void testConnection()
    {
        String apiKey = getApiKeyTextField().getText();
        String host = getHostTextField().getText();

        if(apiKey == null || apiKey.isBlank())
        {
            showAlert(Alert.AlertType.ERROR, "Test Connection", "A valid API Key is required",
                "Please enter an API Key");
            return;
        }

        if(host == null || host.isBlank())
        {
            showAlert(Alert.AlertType.ERROR, "Test Connection", "A valid URL for RadioResolve is required",
                "Please enter a RadioResolve URL");
            return;
        }

        RadioResolveConfiguration configToTest = new RadioResolveConfiguration();
        configToTest.setHost(host);
        configToTest.setApiKey(apiKey);
        configToTest.setNodeName(getNodeNameTextField().getText());
        configToTest.setNodeTimezone(getNodeTimezoneTextField().getText());

        String result = RadioResolveBroadcaster.testConnection(configToTest);

        if(RadioResolveBroadcaster.RESULT_OK.equals(result))
        {
            showAlert(Alert.AlertType.CONFIRMATION, "Test Result", "Success!", "Test successful.");
        }
        else
        {
            showAlert(Alert.AlertType.ERROR, "Test Result", "Test Failed.", "Error: " + result);
        }
    }

    private void showAlert(Alert.AlertType type, String title, String header, String content)
    {
        Alert alert = new Alert(type, content, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.initOwner(getTestButton().getScene().getWindow());
        alert.show();
    }
}
