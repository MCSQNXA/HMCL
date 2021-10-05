/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2021  huangyuhui <huanghongxun2008@126.com> and contributors
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
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.jackhuang.hmcl.ui.versions;

import com.jfoenix.controls.*;
import com.jfoenix.controls.datamodels.treetable.RecursiveTreeObject;
import javafx.beans.property.*;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import org.jackhuang.hmcl.mod.LocalModFile;
import org.jackhuang.hmcl.mod.ModManager;
import org.jackhuang.hmcl.mod.RemoteMod;
import org.jackhuang.hmcl.mod.curse.CurseAddon;
import org.jackhuang.hmcl.mod.modrinth.ModrinthRemoteModRepository;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.construct.JFXCheckBoxTreeTableCell;
import org.jackhuang.hmcl.ui.construct.MDListCell;
import org.jackhuang.hmcl.ui.construct.PageCloseEvent;
import org.jackhuang.hmcl.ui.construct.TwoLineListItem;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jackhuang.hmcl.util.Pair;

import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.jackhuang.hmcl.ui.FXUtils.onEscPressed;
import static org.jackhuang.hmcl.util.Pair.pair;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class ModUpdatesPage extends BorderPane implements DecoratorPage {
    private final ReadOnlyObjectWrapper<State> state = new ReadOnlyObjectWrapper<>(DecoratorPage.State.fromTitle(i18n("mods.check_updates"), -1));

    private final ModManager modManager;
    private final ObservableList<ModUpdateObject> objects;

    public ModUpdatesPage(ModManager modManager, List<LocalModFile.ModUpdate> updates) {
        this.modManager = modManager;

        JFXTreeTableColumn<ModUpdateObject, Boolean> enabledColumn = new JFXTreeTableColumn<>();
        enabledColumn.setCellFactory(column -> new JFXCheckBoxTreeTableCell<>());
        setupCellValueFactory(enabledColumn, ModUpdateObject::enabledProperty);
        enabledColumn.setEditable(true);
        enabledColumn.setMaxWidth(40);
        enabledColumn.setMinWidth(40);

        JFXTreeTableColumn<ModUpdateObject, String> fileNameColumn = new JFXTreeTableColumn<>(i18n("mods.check_updates.file"));
        setupCellValueFactory(fileNameColumn, ModUpdateObject::fileNameProperty);

        JFXTreeTableColumn<ModUpdateObject, String> currentVersionColumn = new JFXTreeTableColumn<>(i18n("mods.check_updates.current_version"));
        setupCellValueFactory(currentVersionColumn, ModUpdateObject::currentVersionProperty);

        JFXTreeTableColumn<ModUpdateObject, String> targetVersionColumn = new JFXTreeTableColumn<>(i18n("mods.check_updates.target_version"));
        setupCellValueFactory(targetVersionColumn, ModUpdateObject::targetVersionProperty);

        JFXTreeTableColumn<ModUpdateObject, String> sourceColumn = new JFXTreeTableColumn<>(i18n("mods.check_updates.source"));
        setupCellValueFactory(sourceColumn, ModUpdateObject::sourceProperty);

        objects = FXCollections.observableList(updates.stream().map(ModUpdateObject::new).collect(Collectors.toList()));

        RecursiveTreeItem<ModUpdateObject> root = new RecursiveTreeItem<>(
                objects,
                RecursiveTreeObject::getChildren);

        JFXTreeTableView<ModUpdateObject> table = new JFXTreeTableView<>(root);
        table.setShowRoot(false);
        table.setEditable(true);
        table.getColumns().setAll(enabledColumn, fileNameColumn, currentVersionColumn, targetVersionColumn, sourceColumn);

        setCenter(table);

        HBox actions = new HBox(8);
        actions.setPadding(new Insets(8));
        actions.setAlignment(Pos.CENTER_RIGHT);

        JFXButton nextButton = new JFXButton(i18n("mods.check_updates.update"));
        nextButton.getStyleClass().add("jfx-button-raised");
        nextButton.setButtonType(JFXButton.ButtonType.RAISED);
        nextButton.setOnAction(e -> updateMods());

        JFXButton cancelButton = new JFXButton(i18n("button.cancel"));
        cancelButton.getStyleClass().add("jfx-button-raised");
        cancelButton.setButtonType(JFXButton.ButtonType.RAISED);
        cancelButton.setOnAction(e -> fireEvent(new PageCloseEvent()));
        onEscPressed(this, cancelButton::fire);

        actions.getChildren().setAll(nextButton, cancelButton);
        setBottom(actions);
    }

    private <T> void setupCellValueFactory(JFXTreeTableColumn<ModUpdateObject, T> column, Function<ModUpdateObject, ObservableValue<T>> mapper) {
        column.setCellValueFactory(param -> {
            if (column.validateValue(param))
                return mapper.apply(param.getValue().getValue());
            else
                return column.getComputedValue(param);
        });
    }

    private void updateMods() {
        Controllers.taskDialog(
                new ModUpdateTask(
                        modManager,
                        objects.stream()
                                .filter(o -> o.enabled.get())
                                .map(object -> pair(object.data.getLocalMod(), object.data.getCandidates().get(0)))
                                .collect(Collectors.toList())),
                i18n("mods.check_updates.update"),
                t -> {
                });
    }

    @Override
    public ReadOnlyObjectWrapper<State> stateProperty() {
        return state;
    }

    public static class ModUpdateCell extends MDListCell<LocalModFile.ModUpdate> {
        TwoLineListItem content = new TwoLineListItem();

        public ModUpdateCell(JFXListView<LocalModFile.ModUpdate> listView) {
            super(listView);

            getContainer().getChildren().setAll(content);
        }

        @Override
        protected void updateControl(LocalModFile.ModUpdate item, boolean empty) {
            if (empty) return;
            ModTranslations.Mod mod = ModTranslations.getModById(item.getLocalMod().getId());
            content.setTitle(mod != null ? mod.getDisplayName() : item.getCurrentVersion().getName());
            content.setSubtitle(item.getLocalMod().getFileName());
            content.getTags().setAll();

            if (item.getCurrentVersion().getSelf() instanceof CurseAddon.LatestFile) {
                content.getTags().add("Curseforge");
            } else if (item.getCurrentVersion().getSelf() instanceof ModrinthRemoteModRepository.ModVersion) {
                content.getTags().add("Modrinth");
            }
        }
    }

    private static class ModUpdateObject extends RecursiveTreeObject<ModUpdateObject> {
        final LocalModFile.ModUpdate data;
        final BooleanProperty enabled = new SimpleBooleanProperty();
        final StringProperty fileName = new SimpleStringProperty();
        final StringProperty currentVersion = new SimpleStringProperty();
        final StringProperty targetVersion = new SimpleStringProperty();
        final StringProperty source = new SimpleStringProperty();

        public ModUpdateObject(LocalModFile.ModUpdate data) {
            this.data = data;

            enabled.set(true);
            fileName.set(data.getLocalMod().getFileName());
            currentVersion.set(data.getCurrentVersion().getVersion());
            targetVersion.set(data.getCandidates().get(0).getVersion());
            switch (data.getCurrentVersion().getSelf().getType()) {
                case CURSEFORGE:
                    source.set(i18n("mods.curseforge"));
                    break;
                case MODRINTH:
                    source.set(i18n("mods.modrinth"));
            }
        }

        public boolean isEnabled() {
            return enabled.get();
        }

        public BooleanProperty enabledProperty() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled.set(enabled);
        }

        public String getFileName() {
            return fileName.get();
        }

        public StringProperty fileNameProperty() {
            return fileName;
        }

        public void setFileName(String fileName) {
            this.fileName.set(fileName);
        }

        public String getCurrentVersion() {
            return currentVersion.get();
        }

        public StringProperty currentVersionProperty() {
            return currentVersion;
        }

        public void setCurrentVersion(String currentVersion) {
            this.currentVersion.set(currentVersion);
        }

        public String getTargetVersion() {
            return targetVersion.get();
        }

        public StringProperty targetVersionProperty() {
            return targetVersion;
        }

        public void setTargetVersion(String targetVersion) {
            this.targetVersion.set(targetVersion);
        }

        public String getSource() {
            return source.get();
        }

        public StringProperty sourceProperty() {
            return source;
        }

        public void setSource(String source) {
            this.source.set(source);
        }
    }

    public static class ModUpdateTask extends Task<Void> {
        private final Collection<Task<?>> dependents;

        ModUpdateTask(ModManager modManager, List<Pair<LocalModFile, RemoteMod.Version>> mods) {
            setStage("mods.check_updates.update");
            getProperties().put("total", mods.size());

            dependents = mods.stream()
                    .map(mod -> {
                        return Task
                                .supplyAsync(() -> {
                                    return null;
                                })
                                .setName(mod.getKey().getName())
                                .setSignificance(TaskSignificance.MAJOR)
                                .withCounter("mods.check_updates.update");
                    })
                    .collect(Collectors.toList());
        }

        @Override
        public Collection<Task<?>> getDependents() {
            return dependents;
        }

        @Override
        public boolean doPreExecute() {
            return true;
        }

        @Override
        public void preExecute() {
            notifyPropertiesChanged();
        }

        @Override
        public boolean isRelyingOnDependents() {
            return false;
        }

        @Override
        public void execute() throws Exception {
        }
    }
}
