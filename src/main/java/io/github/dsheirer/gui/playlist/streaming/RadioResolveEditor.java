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
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import javafx.beans.binding.Bindings;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;

/**
 * RadioResolve completed call upload configuration editor.
 */
public class RadioResolveEditor extends AbstractBroadcastEditor<RadioResolveConfiguration>
{
    private static final DateTimeFormatter STATUS_TIME_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z").withZone(ZoneId.systemDefault());

    private PasswordField mApiKeyTextField;
    private TextField mHostTextField;
    private TextField mNodeNameTextField;
    private TextField mNodeTimezoneTextField;
    private CheckBox mIgnoreCertificateErrorsCheckBox;
    private IntegerTextField mMaxAgeTextField;
    private Button mTestButton;
    private Label mLastUploadSuccessLabel;
    private Label mLastUploadFailureLabel;
    private Label mLastCheckInSuccessLabel;
    private Label mLastCheckInFailureLabel;
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
        getIgnoreCertificateErrorsCheckBox().setDisable(item == null);
        getMaxAgeTextField().setDisable(item == null);
        getTestButton().setDisable(item == null);
        bindStatusLabels(item);

        if(item != null)
        {
            getApiKeyTextField().setText(item.getApiKey());
            getHostTextField().setText(item.getHost());
            getNodeNameTextField().setText(item.getNodeName());
            getNodeTimezoneTextField().setText(item.getNodeTimezone());
            getIgnoreCertificateErrorsCheckBox().setSelected(item.isIgnoreCertificateErrors());
            getMaxAgeTextField().set((int)(item.getMaximumRecordingAge() / 1000));
        }
        else
        {
            getApiKeyTextField().setText(null);
            getHostTextField().setText(null);
            getNodeNameTextField().setText(null);
            getNodeTimezoneTextField().setText(null);
            getIgnoreCertificateErrorsCheckBox().setSelected(false);
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
            getItem().setIgnoreCertificateErrors(getIgnoreCertificateErrorsCheckBox().isSelected());
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

            Label ignoreCertificateErrorsLabel = new Label("Ignore Certificate Errors");
            GridPane.setHalignment(ignoreCertificateErrorsLabel, HPos.RIGHT);
            GridPane.setConstraints(ignoreCertificateErrorsLabel, 0, ++row);
            mEditorPane.getChildren().add(ignoreCertificateErrorsLabel);

            GridPane.setConstraints(getIgnoreCertificateErrorsCheckBox(), 1, row);
            mEditorPane.getChildren().add(getIgnoreCertificateErrorsCheckBox());

            Label maxAgeLabel = new Label("Max Recording Age (seconds)");
            GridPane.setHalignment(maxAgeLabel, HPos.RIGHT);
            GridPane.setConstraints(maxAgeLabel, 0, ++row);
            mEditorPane.getChildren().add(maxAgeLabel);

            GridPane.setConstraints(getMaxAgeTextField(), 1, row);
            mEditorPane.getChildren().add(getMaxAgeTextField());

            GridPane.setConstraints(getTestButton(), 1, ++row);
            mEditorPane.getChildren().add(getTestButton());

            Label lastUploadSuccessLabel = new Label("Last Upload Accepted");
            GridPane.setHalignment(lastUploadSuccessLabel, HPos.RIGHT);
            GridPane.setConstraints(lastUploadSuccessLabel, 0, ++row);
            mEditorPane.getChildren().add(lastUploadSuccessLabel);

            GridPane.setConstraints(getLastUploadSuccessLabel(), 1, row);
            mEditorPane.getChildren().add(getLastUploadSuccessLabel());

            Label lastUploadFailureLabel = new Label("Last Upload Error");
            GridPane.setHalignment(lastUploadFailureLabel, HPos.RIGHT);
            GridPane.setConstraints(lastUploadFailureLabel, 0, ++row);
            mEditorPane.getChildren().add(lastUploadFailureLabel);

            GridPane.setConstraints(getLastUploadFailureLabel(), 1, row);
            mEditorPane.getChildren().add(getLastUploadFailureLabel());

            Label lastCheckInSuccessLabel = new Label("Last Check-In Accepted");
            GridPane.setHalignment(lastCheckInSuccessLabel, HPos.RIGHT);
            GridPane.setConstraints(lastCheckInSuccessLabel, 0, ++row);
            mEditorPane.getChildren().add(lastCheckInSuccessLabel);

            GridPane.setConstraints(getLastCheckInSuccessLabel(), 1, row);
            mEditorPane.getChildren().add(getLastCheckInSuccessLabel());

            Label lastCheckInFailureLabel = new Label("Last Check-In Error");
            GridPane.setHalignment(lastCheckInFailureLabel, HPos.RIGHT);
            GridPane.setConstraints(lastCheckInFailureLabel, 0, ++row);
            mEditorPane.getChildren().add(lastCheckInFailureLabel);

            GridPane.setConstraints(getLastCheckInFailureLabel(), 1, row);
            mEditorPane.getChildren().add(getLastCheckInFailureLabel());
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

    private CheckBox getIgnoreCertificateErrorsCheckBox()
    {
        if(mIgnoreCertificateErrorsCheckBox == null)
        {
            mIgnoreCertificateErrorsCheckBox = new CheckBox();
            mIgnoreCertificateErrorsCheckBox.setDisable(true);
            mIgnoreCertificateErrorsCheckBox.selectedProperty().addListener((observable, oldValue, newValue) ->
                modifiedProperty().set(true));
        }

        return mIgnoreCertificateErrorsCheckBox;
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

    private Label getLastUploadSuccessLabel()
    {
        if(mLastUploadSuccessLabel == null)
        {
            mLastUploadSuccessLabel = new Label("Never");
        }

        return mLastUploadSuccessLabel;
    }

    private Label getLastUploadFailureLabel()
    {
        if(mLastUploadFailureLabel == null)
        {
            mLastUploadFailureLabel = new Label("None");
        }

        return mLastUploadFailureLabel;
    }

    private Label getLastCheckInSuccessLabel()
    {
        if(mLastCheckInSuccessLabel == null)
        {
            mLastCheckInSuccessLabel = new Label("Never");
        }

        return mLastCheckInSuccessLabel;
    }

    private Label getLastCheckInFailureLabel()
    {
        if(mLastCheckInFailureLabel == null)
        {
            mLastCheckInFailureLabel = new Label("None");
        }

        return mLastCheckInFailureLabel;
    }

    private void bindStatusLabels(RadioResolveConfiguration item)
    {
        getLastUploadSuccessLabel().textProperty().unbind();
        getLastUploadFailureLabel().textProperty().unbind();
        getLastCheckInSuccessLabel().textProperty().unbind();
        getLastCheckInFailureLabel().textProperty().unbind();

        if(item == null)
        {
            getLastUploadSuccessLabel().setText("Never");
            getLastUploadFailureLabel().setText("None");
            getLastCheckInSuccessLabel().setText("Never");
            getLastCheckInFailureLabel().setText("None");
            return;
        }

        getLastUploadSuccessLabel().textProperty().bind(Bindings.createStringBinding(
            () -> formatTimestamp(item.getLastSuccessfulUploadEpochMilliseconds()),
            item.lastSuccessfulUploadEpochMillisecondsProperty()));
        getLastUploadFailureLabel().textProperty().bind(Bindings.createStringBinding(
            () -> formatFailure(item.getLastFailedUploadEpochMilliseconds(), item.getLastUploadFailureMessage()),
            item.lastFailedUploadEpochMillisecondsProperty(), item.lastUploadFailureMessageProperty()));
        getLastCheckInSuccessLabel().textProperty().bind(Bindings.createStringBinding(
            () -> formatTimestamp(item.getLastSuccessfulCheckInEpochMilliseconds()),
            item.lastSuccessfulCheckInEpochMillisecondsProperty()));
        getLastCheckInFailureLabel().textProperty().bind(Bindings.createStringBinding(
            () -> formatFailure(item.getLastFailedCheckInEpochMilliseconds(), item.getLastCheckInFailureMessage()),
            item.lastFailedCheckInEpochMillisecondsProperty(), item.lastCheckInFailureMessageProperty()));
    }

    private static String formatTimestamp(long epochMilliseconds)
    {
        return epochMilliseconds > 0 ? STATUS_TIME_FORMATTER.format(Instant.ofEpochMilli(epochMilliseconds)) : "Never";
    }

    private static String formatFailure(long epochMilliseconds, String message)
    {
        if(epochMilliseconds <= 0)
        {
            return "None";
        }

        String safeMessage = message != null && !message.isBlank() ? message : "Error";
        return formatTimestamp(epochMilliseconds) + " - " + safeMessage;
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
        configToTest.setIgnoreCertificateErrors(getIgnoreCertificateErrorsCheckBox().isSelected());

        RadioResolveBroadcaster.TestResult result = RadioResolveBroadcaster.testConnectionDetailed(configToTest);

        if(result.success())
        {
            showAlert(Alert.AlertType.CONFIRMATION, "Test Result", "Success!", result.displayMessage());
        }
        else
        {
            showAlert(Alert.AlertType.ERROR, "Test Result", "Test Failed.", "Error: " + result.message());
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
