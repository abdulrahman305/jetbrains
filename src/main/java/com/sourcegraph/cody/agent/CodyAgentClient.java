package com.sourcegraph.cody.agent;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.sourcegraph.cody.agent.protocol.*;
import com.sourcegraph.cody.agent.protocol_generated.DisplayCodeLensParams;
import com.sourcegraph.cody.agent.protocol_generated.EditTask;
import com.sourcegraph.cody.ui.NativeWebviewProvider;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import org.eclipse.lsp4j.jsonrpc.services.JsonNotification;
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Implementation of the client part of the Cody agent protocol. This class dispatches the requests
 * and notifications sent by the agent.
 */
@SuppressWarnings("unused")
public class CodyAgentClient {
  private static final Logger logger = Logger.getInstance(CodyAgentClient.class);

  @NotNull NativeWebviewProvider webview;

  CodyAgentClient(@NotNull NativeWebviewProvider webviewProvider) {
    this.webview = webviewProvider;
  }

  // TODO: Remove this once we stop sniffing postMessage.
  // Callback that is invoked when the agent sends a "chat/updateMessageInProgress" notification.
  @Nullable Consumer<WebviewPostMessageParams> onNewMessage;

  // Callback that is invoked when the agent sends a "setConfigFeatures" message.
  @Nullable ConfigFeaturesObserver onSetConfigFeatures;

  // Callback that is invoked on webview messages which aren't handled by onNewMessage or
  // onSetConfigFeatures
  @Nullable Consumer<WebviewPostMessageParams> onReceivedWebviewMessageTODODeleteThis;

  // Callback for the "editTask/didUpdate" notification from the agent.
  @Nullable Consumer<EditTask> onEditTaskDidUpdate;

  // Callback for the "editTask/didDelete" notification from the agent.
  @Nullable Consumer<EditTask> onEditTaskDidDelete;

  // Callback for the "editTask/codeLensesDisplay" notification from the agent.
  @Nullable Consumer<DisplayCodeLensParams> onCodeLensesDisplay;

  // Callback for the "textDocument/edit" request from the agent.
  @Nullable Function<TextDocumentEditParams, Boolean> onTextDocumentEdit;

  // Callback for the "textDocument/show" request from the agent.
  @Nullable Function<TextDocumentShowParams, Boolean> onTextDocumentShow;

  // Callback for the "textDocument/openUntitledDocument" request from the agent.
  @Nullable Function<UntitledTextDocument, ProtocolTextDocument> onOpenUntitledDocument;

  // Callback for the "workspace/edit" request from the agent.
  @Nullable Function<WorkspaceEditParams, Boolean> onWorkspaceEdit;

  @Nullable Consumer<DebugMessage> onDebugMessage;

  @JsonNotification("editTask/didUpdate")
  public CompletableFuture<Void> editTaskDidUpdate(EditTask params) {
    return acceptOnEventThread("editTask/didUpdate", onEditTaskDidUpdate, params);
  }

  @JsonNotification("editTask/didDelete")
  public CompletableFuture<Void> editTaskDidDelete(EditTask params) {
    return acceptOnEventThread("editTask/didDelete", onEditTaskDidDelete, params);
  }

  @JsonNotification("codeLenses/display")
  public void codeLensesDisplay(DisplayCodeLensParams params) {
    acceptOnEventThread("codeLenses/display", onCodeLensesDisplay, params);
  }

  @Nullable Function<OpenExternalParams, Boolean> onOpenExternal;

  @JsonRequest("env/openExternal")
  public CompletableFuture<Boolean> ignoreTest(@NotNull OpenExternalParams params) {
    return acceptOnEventThread("env/openExternal", onOpenExternal, params);
  }

  @Nullable Consumer<Void> onRemoteRepoDidChange;

  @JsonNotification("remoteRepo/didChange")
  public void remoteRepoDidChange() {
    if (onRemoteRepoDidChange != null) {
      onRemoteRepoDidChange.accept(null);
    }
  }

  @Nullable Consumer<RemoteRepoFetchState> onRemoteRepoDidChangeState;

  @JsonNotification("remoteRepo/didChangeState")
  public void remoteRepoDidChangeState(RemoteRepoFetchState state) {
    if (onRemoteRepoDidChangeState != null) {
      onRemoteRepoDidChangeState.accept(state);
    }
  }

  @Nullable Consumer<Void> onIgnoreDidChange;

  @JsonNotification("ignore/didChange")
  public void ignoreDidChange() {
    if (onIgnoreDidChange != null) {
      onIgnoreDidChange.accept(null);
    }
  }

  @JsonRequest("textDocument/edit")
  public CompletableFuture<Boolean> textDocumentEdit(TextDocumentEditParams params) {
    return acceptOnEventThread("textDocument/edit", onTextDocumentEdit, params);
  }

  @JsonRequest("textDocument/show")
  public CompletableFuture<Boolean> textDocumentShow(TextDocumentShowParams params) {
    return acceptOnEventThread("textDocument/show", onTextDocumentShow, params);
  }

  @JsonRequest("textDocument/openUntitledDocument")
  public CompletableFuture<ProtocolTextDocument> openUntitledDocument(UntitledTextDocument params) {
    if (onOpenUntitledDocument == null) {
      return CompletableFuture.failedFuture(
          new Exception("No callback registered for textDocument/openUntitledDocument"));
    } else {
      return CompletableFuture.completedFuture(onOpenUntitledDocument.apply(params));
    }
  }

  @JsonRequest("workspace/edit")
  public CompletableFuture<Boolean> workspaceEdit(WorkspaceEditParams params) {
    return acceptOnEventThread("workspace/edit", onWorkspaceEdit, params);
  }

  /**
   * Helper to run client request/notification handlers on the IntelliJ event thread. Use this
   * helper for handlers that require access to the IntelliJ editor, for example to read the text
   * contents of the open editor.
   */
  private <T, R> @NotNull CompletableFuture<R> acceptOnEventThread(
      String name, @Nullable Function<T, R> callback, T params) {
    CompletableFuture<R> result = new CompletableFuture<>();
    ApplicationManager.getApplication()
        .invokeLater(
            () -> {
              try {
                if (callback != null) {
                  result.complete(callback.apply(params));
                } else {
                  result.completeExceptionally(new Exception("No callback registered for " + name));
                }
              } catch (Exception e) {
                result.completeExceptionally(e);
              }
            });
    return result;
  }

  private <T> @NotNull CompletableFuture<Void> acceptOnEventThread(
      String name, @Nullable Consumer<T> callback, T params) {
    Function<T, Void> fun =
        callback == null
            ? null
            : t -> {
              callback.accept(params);
              return null;
            };
    return acceptOnEventThread(name, fun, params);
  }

  // TODO: Delete this
  // Webviews
  @JsonRequest("webview/create")
  public CompletableFuture<Void> webviewCreate(WebviewCreateParams params) {
    logger.error("webview/create This request should not happen if you are using chat/new.");
    return CompletableFuture.completedFuture(null);
  }

  // =============
  // Notifications
  // =============

  @JsonNotification("debug/message")
  public void debugMessage(@NotNull DebugMessage msg) {
    logger.warn(String.format("%s: %s", msg.getChannel(), msg.getMessage()));
    if (onDebugMessage != null) {
      onDebugMessage.accept(msg);
    }
  }

  // ================================================
  // Webviews, forwarded to the NativeWebviewProvider
  // ================================================

  @JsonNotification("webview/createWebviewPanel")
  public void webviewCreateWebviewPanel(@NotNull WebviewCreateWebviewPanelParams params) {
    this.webview.createPanel(params);
  }

  @JsonNotification("webview/postMessageStringEncoded")
  public void webviewPostMessageStringEncoded(
      @NotNull WebviewPostMessageStringEncodedParams params) {
    this.webview.receivedPostMessage(params);
  }

  @JsonNotification("webview/registerWebviewViewProvider")
  public void webviewRegisterWebviewViewProvider(
      @NotNull WebviewRegisterWebviewViewProviderParams params) {
    this.webview.registerViewProvider(params);
  }

  @JsonNotification("webview/setHtml")
  public void webviewTitle(@NotNull WebviewSetHtmlParams params) {
    this.webview.setHtml(params);
  }

  @JsonNotification("webview/setIconPath")
  public void webviewSetIconPath(@NotNull WebviewSetIconPathParams params) {
    // TODO: Implement this.
    System.out.println("TODO, implement webview/setIconPath");
  }

  @JsonNotification("webview/setOptions")
  public void webviewTitle(@NotNull WebviewSetOptionsParams params) {
    this.webview.setOptions(params);
  }

  @JsonNotification("webview/setTitle")
  public void webviewTitle(@NotNull WebviewSetTitleParams params) {
    this.webview.setTitle(params);
  }

  @JsonNotification("webview/reveal")
  public void webviewReveal(@NotNull WebviewRevealParams params) {
    // TODO: Implement this.
    System.out.println("TODO, implement webview/reveal");
  }

  @JsonNotification("webview/dispose")
  public void webviewDispose(@NotNull WebviewDisposeParams params) {
    // TODO: Implement this.
    System.out.println("TODO, implement webview/dispose");
  }

  // TODO: Remove this
  @JsonNotification("webview/postMessage")
  public void webviewPostMessage(@NotNull WebviewPostMessageParams params) {
    ExtensionMessage extensionMessage = params.getMessage();

    if (onNewMessage != null
        && extensionMessage.getType().equals(ExtensionMessage.Type.TRANSCRIPT)) {
      ApplicationManager.getApplication().invokeLater(() -> onNewMessage.accept(params));
      return;
    }

    if (onSetConfigFeatures != null
        && extensionMessage.getType().equals(ExtensionMessage.Type.SET_CONFIG_FEATURES)) {
      ApplicationManager.getApplication()
          .invokeLater(() -> onSetConfigFeatures.update(extensionMessage.getConfigFeatures()));
      return;
    }

    if (onReceivedWebviewMessageTODODeleteThis != null) {
      ApplicationManager.getApplication()
          .invokeLater(() -> onReceivedWebviewMessageTODODeleteThis.accept(params));
      return;
    }

    logger.debug(String.format("webview/postMessage %s: %s", params.getId(), params.getMessage()));
  }
}
