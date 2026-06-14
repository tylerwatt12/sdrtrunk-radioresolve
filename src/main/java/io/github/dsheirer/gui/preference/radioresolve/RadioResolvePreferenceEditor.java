/*
 * *****************************************************************************
 * Copyright (C) 2014-2026 Dennis Sheirer
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

package io.github.dsheirer.gui.preference.radioresolve;

import io.github.dsheirer.audio.broadcast.radioresolve.RadioResolveBroadcaster;
import io.github.dsheirer.audio.broadcast.radioresolve.RadioResolveConfiguration;
import io.github.dsheirer.eventbus.MyEventBus;
import io.github.dsheirer.gui.playlist.ViewPlaylistRequest;
import io.github.dsheirer.playlist.PlaylistManager;
import io.github.dsheirer.preference.UserPreferences;
import io.github.dsheirer.preference.radioresolve.RadioResolvePreference;
import io.github.dsheirer.radioresolve.RadioResolveConfigurations;
import io.github.dsheirer.radioresolve.RadioResolveDoctor;
import io.github.dsheirer.radioresolve.RadioResolveNodeService;
import io.github.dsheirer.util.ThreadPool;
import java.util.Optional;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextArea;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.controlsfx.control.ToggleSwitch;

/**
 * RadioResolve node integration preference editor.
 */
public class RadioResolvePreferenceEditor extends HBox
{
    private final RadioResolvePreference mPreference;
    private final PlaylistManager mPlaylistManager;
    private VBox mContentBox;
    private ToggleSwitch mCheckInEnabledToggle;
    private Spinner<Integer> mCheckInIntervalSpinner;
    private ToggleSwitch mTelemetryEnabledToggle;
    private ToggleSwitch mRemoteCommandsEnabledToggle;
    private Spinner<Integer> mClockWarnOffsetSpinner;
    private Spinner<Integer> mClockBlockOffsetSpinner;

    /**
     * Constructs an instance.
     * @param userPreferences user preferences
     * @param playlistManager playlist manager
     */
    public RadioResolvePreferenceEditor(UserPreferences userPreferences, PlaylistManager playlistManager)
    {
        mPreference = userPreferences.getRadioResolvePreference();
        mPlaylistManager = playlistManager;
        HBox.setHgrow(getContentBox(), Priority.ALWAYS);
        getChildren().add(getContentBox());

        if(mPlaylistManager != null)
        {
            mPlaylistManager.getBroadcastModel().getConfiguredBroadcasts()
                .addListener((ListChangeListener)change -> Platform.runLater(this::refresh));
        }

        refresh();
    }

    private VBox getContentBox()
    {
        if(mContentBox == null)
        {
            mContentBox = new VBox(10);
            mContentBox.setPadding(new Insets(10, 10, 10, 10));
            mContentBox.setMaxWidth(Double.MAX_VALUE);
        }

        return mContentBox;
    }

    private void refresh()
    {
        getContentBox().getChildren().clear();

        Label title = new Label("RadioResolve Node Integration");
        getContentBox().getChildren().add(title);

        Optional<RadioResolveConfiguration> enabledConfiguration = getEnabledConfiguration();

        if(enabledConfiguration.isEmpty())
        {
            Label message = new Label(getMissingConfigurationMessage());
            message.setWrapText(true);
            Button openPlaylistButton = new Button("Open Playlist Editor");
            openPlaylistButton.setOnAction(event -> MyEventBus.getGlobalEventBus().post(new ViewPlaylistRequest()));
            getContentBox().getChildren().addAll(message, openPlaylistButton);
            return;
        }

        RadioResolveConfiguration configuration = enabledConfiguration.get();
        getContentBox().getChildren().add(createStatusPane(configuration));

        Separator separator = new Separator(Orientation.HORIZONTAL);
        getContentBox().getChildren().add(separator);

        getContentBox().getChildren().add(createOptionsPane());
    }

    private String getMissingConfigurationMessage()
    {
        if(RadioResolveConfigurations.findAny(mPlaylistManager.getBroadcastModel()).isPresent())
        {
            return "Enable the RadioResolve stream in Playlist Editor > Streaming to use RadioResolve node tools.";
        }

        return "Add and enable a RadioResolve stream in Playlist Editor > Streaming to use RadioResolve node tools.";
    }

    private GridPane createStatusPane(RadioResolveConfiguration configuration)
    {
        GridPane gridPane = createGridPane();
        int row = 0;
        addReadOnlyValue(gridPane, "Stream:", configuration.getName(), row++);
        addReadOnlyValue(gridPane, "Server URL:", configuration.getHost(), row++);
        addReadOnlyValue(gridPane, "API Key:", configuration.getApiKey() == null ||
            configuration.getApiKey().isBlank() ? "Missing" : "Configured", row++);
        addReadOnlyValue(gridPane, "Node Name:", configuration.getNodeName(), row++);
        addReadOnlyValue(gridPane, "Node Timezone:", configuration.getNodeTimezone(), row++);

        HBox buttonBox = new HBox(8);
        buttonBox.getChildren().addAll(createButton("Test Connection", () ->
                RadioResolveBroadcaster.testConnection(getEnabledConfiguration().orElse(configuration))),
            createButton("Send Check-In", () ->
                new RadioResolveNodeService(mPreference, this::getEnabledConfiguration).sendCheckIn().message()),
            createDoctorButton());
        gridPane.add(buttonBox, 0, row, 2, 1);
        return gridPane;
    }

    private GridPane createOptionsPane()
    {
        GridPane gridPane = createGridPane();
        int row = 0;
        addSwitch(gridPane, "Enable Node Check-Ins", getCheckInEnabledToggle(), row++);
        addLabeledControl(gridPane, "Check-In Interval:", getCheckInIntervalSpinner(), row++);
        addSwitch(gridPane, "Upload RF/System Telemetry", getTelemetryEnabledToggle(), row++);
        addSwitch(gridPane, "Allow Safe Remote Commands", getRemoteCommandsEnabledToggle(), row++);
        addLabeledControl(gridPane, "Clock Warn Offset (ms):", getClockWarnOffsetSpinner(), row++);
        addLabeledControl(gridPane, "Clock Block Offset (ms):", getClockBlockOffsetSpinner(), row++);
        return gridPane;
    }

    private GridPane createGridPane()
    {
        GridPane gridPane = new GridPane();
        gridPane.setHgap(10);
        gridPane.setVgap(10);
        gridPane.setMaxWidth(Double.MAX_VALUE);

        ColumnConstraints c1 = new ColumnConstraints();
        c1.setPercentWidth(30);
        ColumnConstraints c2 = new ColumnConstraints();
        c2.setHgrow(Priority.ALWAYS);
        gridPane.getColumnConstraints().addAll(c1, c2);
        return gridPane;
    }

    private void addReadOnlyValue(GridPane gridPane, String labelText, String value, int row)
    {
        Label label = new Label(labelText);
        GridPane.setHalignment(label, HPos.RIGHT);
        gridPane.add(label, 0, row);
        Label valueLabel = new Label(value == null || value.isBlank() ? "-" : value);
        valueLabel.setWrapText(true);
        gridPane.add(valueLabel, 1, row);
    }

    private void addLabeledControl(GridPane gridPane, String labelText, Node control, int row)
    {
        Label label = new Label(labelText);
        GridPane.setHalignment(label, HPos.RIGHT);
        gridPane.add(label, 0, row);
        gridPane.add(control, 1, row);
    }

    private void addSwitch(GridPane gridPane, String labelText, ToggleSwitch toggleSwitch, int row)
    {
        GridPane.setHalignment(toggleSwitch, HPos.RIGHT);
        gridPane.add(toggleSwitch, 0, row);
        gridPane.add(new Label(labelText), 1, row);
    }

    private Button createButton(String label, ResultSupplier supplier)
    {
        Button button = new Button(label);
        button.setOnAction(event -> runAsync(label, supplier));
        return button;
    }

    private Button createDoctorButton()
    {
        Button button = new Button("Run Doctor");
        button.setOnAction(event -> ThreadPool.CACHED.submit(() -> {
            RadioResolveDoctor.Report report = getEnabledConfiguration()
                .map(configuration -> RadioResolveDoctor.run(configuration, mPreference))
                .orElse(new RadioResolveDoctor.Report(java.util.List.of(
                    RadioResolveDoctor.Check.error("RadioResolve Stream", "An enabled RadioResolve stream is required"))));
            Platform.runLater(() -> showDoctorReport(report));
        }));
        return button;
    }

    private ToggleSwitch getCheckInEnabledToggle()
    {
        if(mCheckInEnabledToggle == null)
        {
            mCheckInEnabledToggle = new ToggleSwitch();
            mCheckInEnabledToggle.setSelected(mPreference.isCheckInEnabled());
            mCheckInEnabledToggle.selectedProperty().addListener((observable, oldValue, enabled) ->
                mPreference.setCheckInEnabled(enabled));
        }

        return mCheckInEnabledToggle;
    }

    private Spinner<Integer> getCheckInIntervalSpinner()
    {
        if(mCheckInIntervalSpinner == null)
        {
            mCheckInIntervalSpinner = new Spinner<>(15, 3600, mPreference.getCheckInIntervalSeconds(), 15);
            mCheckInIntervalSpinner.valueProperty().addListener((observable, oldValue, newValue) ->
                mPreference.setCheckInIntervalSeconds(newValue));
        }

        return mCheckInIntervalSpinner;
    }

    private ToggleSwitch getTelemetryEnabledToggle()
    {
        if(mTelemetryEnabledToggle == null)
        {
            mTelemetryEnabledToggle = new ToggleSwitch();
            mTelemetryEnabledToggle.setSelected(mPreference.isTelemetryEnabled());
            mTelemetryEnabledToggle.selectedProperty().addListener((observable, oldValue, enabled) ->
                mPreference.setTelemetryEnabled(enabled));
        }

        return mTelemetryEnabledToggle;
    }

    private ToggleSwitch getRemoteCommandsEnabledToggle()
    {
        if(mRemoteCommandsEnabledToggle == null)
        {
            mRemoteCommandsEnabledToggle = new ToggleSwitch();
            mRemoteCommandsEnabledToggle.setSelected(mPreference.isRemoteCommandsEnabled());
            mRemoteCommandsEnabledToggle.selectedProperty().addListener((observable, oldValue, enabled) ->
                mPreference.setRemoteCommandsEnabled(enabled));
        }

        return mRemoteCommandsEnabledToggle;
    }

    private Spinner<Integer> getClockWarnOffsetSpinner()
    {
        if(mClockWarnOffsetSpinner == null)
        {
            mClockWarnOffsetSpinner = new Spinner<>(0, 60000, mPreference.getClockWarnOffsetMilliseconds(), 100);
            mClockWarnOffsetSpinner.valueProperty().addListener((observable, oldValue, newValue) ->
                mPreference.setClockWarnOffsetMilliseconds(newValue));
        }

        return mClockWarnOffsetSpinner;
    }

    private Spinner<Integer> getClockBlockOffsetSpinner()
    {
        if(mClockBlockOffsetSpinner == null)
        {
            mClockBlockOffsetSpinner = new Spinner<>(0, 300000, mPreference.getClockBlockOffsetMilliseconds(), 500);
            mClockBlockOffsetSpinner.valueProperty().addListener((observable, oldValue, newValue) ->
                mPreference.setClockBlockOffsetMilliseconds(newValue));
        }

        return mClockBlockOffsetSpinner;
    }

    private Optional<RadioResolveConfiguration> getEnabledConfiguration()
    {
        return RadioResolveConfigurations.findEnabled(mPlaylistManager.getBroadcastModel());
    }

    private void runAsync(String title, ResultSupplier supplier)
    {
        ThreadPool.CACHED.submit(() -> {
            String result;

            try
            {
                result = supplier.get();
            }
            catch(Exception e)
            {
                result = e.getMessage();
            }

            String message = result;
            Platform.runLater(() -> showMessage(title, message));
        });
    }

    private void showMessage(String title, String message)
    {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(title);
        Label wrappingLabel = new Label(message);
        wrappingLabel.setWrapText(true);
        alert.getDialogPane().setContent(wrappingLabel);
        alert.show();
    }

    private void showDoctorReport(RadioResolveDoctor.Report report)
    {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("RadioResolve Doctor");
        alert.setHeaderText("RadioResolve Doctor");

        TextArea textArea = new TextArea(report.toSummaryString());
        textArea.setEditable(false);
        textArea.setWrapText(true);
        textArea.setPrefColumnCount(80);
        textArea.setPrefRowCount(12);
        alert.getDialogPane().setContent(textArea);
        alert.show();
    }

    @FunctionalInterface
    private interface ResultSupplier
    {
        String get();
    }
}
