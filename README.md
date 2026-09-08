# BurpIO

BurpIO is a [Burp Suite](https://portswigger.net/burp/pro) extension developed to speed-up common input/output operations I use in my workflow.

## Features

### Context Menu Options

The following options can be used by right-clicking on request/response panels, such as the repeater or request/response lists such as proxy or organizer. Most of the options support selecting multiple requests in lists.

#### Copy as Markdown

Allows copying request/response pairs to the clipboard, formatted for Markdown reports.

"Copy as Markdown" will automatically strip most headers and truncate bodies after 1000 characters. "Copy as Markdown (Full)" will not strip headers or truncate bodies.

If you select a portion of the body in the request or response panel before copying, the body will always include the selected portion and truncate text around the selected text. Useful if you have large responses, and you want to highlight a specific evidence (ex: XSS payload).
Due to limitation in the extension API, you can select context only in the focused panel, so you cannot have a selection on both request and response.

This supports both Markdown and Dradis syntax. You can configure which to use, alongside which headers and cookies to keep and truncate sizes in Burp Suite Settings > Extensions > BurpIO.

#### Extract Strings

Allows extracting parameters from selected requests and responses and copies them to the clipboard.

The most common use case is to use the proxy filter to select the requests you are interested in, then Ctrl+A and extract the parameters you need. Useful to extract all values for a specific parameter (ex: `?action=`) or header.

Supports using regexes on body. If you specify a regex with no capturing group, the entire match is returned. If you have one or more capturing group, all capturing groups are returned separated by the tab character.

Returns one result per line so you can easily pipe it to any Unix tool for processing text. If there are tab or new line characters in the output, they are encoded as `\t` or `\n`.

#### History Search

Searches the proxy history for a selected value to show where else it appears. Useful for tracing where a value originates or where it later gets used.

Requires selecting a value in a request or response to run the search:
- Selecting in a request searches responses, to find the response that produced the value.
- Selecting in a response searches requests, to find where the value is later used.

For example, if you spot a token in a request, select it and run this function. This will return a list of all responses containing that token, allowing you to find the request that generated it.

To match this "where does it come from / where does it go" intent, the search only looks at entries before the initiating request when searching responses, and after it when searching requests. This directional filter can be toggled off in the search window.

Results are displayed in a table. Double-click a row, or select it and press "Highlight Selected", to highlight the corresponding entry in the history tab.   

#### Save Response Body

Saves the response body of all selected requests to the filesystem. Filenames and extensions are automatically determined by the path when possible. 

### Quick Session

Keyboard shortcuts to quickly save and load session Headers from request editors. Useful when testing authorization checks to quickly switch between different users.

By default, the `Authorization` and `Cookie` headers are stored to a session. This can be changed in Burp Suite Settings > Extensions > BurpIO.

There are 10 possible session slots each bound to one of the 10 digits on the keyboard.

`Ctrl+Shift+#` stores headers to the respective session slot, where `#` is a digit 0-9.
`Ctrl+#` loads headers from the respective session slot.



## Install

Requires Burp Suite 2026.4 or later.

Download the jar from the [latest release](https://github.com/mattiadr/BurpIO/releases/latest), then load it from the extensions menu in Burp Suite.

## Build

Only tagged commits are available in releases, so if you want (possibly broken or incomplete) builds you can build it yourself.

Requires JDK 21 or later installed. All other dependencies are handled by Gradle.

```bash
git clone https://github.com/mattiadr/BurpIO.git
cd BurpIO
./gradlew shadowJar
```

The built jar can be found at `build/libs/BurpIO.jar`.
