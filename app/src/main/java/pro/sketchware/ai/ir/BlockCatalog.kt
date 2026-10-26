package pro.sketchware.ai.ir

import a.a.a.kq
import a.a.a.xB
import android.content.Context
import android.graphics.Color
import dev.aldi.sayuti.block.ExtraBlockFile
import mod.hey.studios.editor.manage.block.v2.BlockLoader
import mod.hilal.saif.blocks.BlocksHandler

data class BlockSignature(
    val opCode: String,
    val type: String,       // " " (regular statement), "c" (if/loop), "e" (if-else), "f" (terminal), "b" (boolean), "d" (number), "s" (string), "v" (view/object), "l" (list), "a" (map)
    val typeName: String = "",
    val spec: String,
    val color: Int,
    val category: String = "General",
    val description: String = ""
) {
    val paramPlaceholders: List<String>
        get() = PARAM_REGEX.findAll(spec).map { it.value }.toList()

    companion object {
        private val PARAM_REGEX = Regex("%[bcdsmla]|%[a-zA-Z0-9_.]+")
    }
}

object BlockCatalog {

    private val coreBlocks: Map<String, BlockSignature> by lazy {
        val list = listOf(
            // Variables & Lists & Maps
            BlockSignature("getVar", "s", "", "%s", 0xFFEE7D16.toInt(), "Variable", "Read variable value (set type to 'b' for boolean, 'd' for number, 's' for string, 'a' for map)"),
            BlockSignature("setVarBoolean", " ", "", "set %m.varBool to %b", 0xFFEE7D16.toInt(), "Variable", "Set boolean variable [varName, boolValue]"),
            BlockSignature("setVarInt", " ", "", "set %m.varInt to %d", 0xFFEE7D16.toInt(), "Variable", "Set number variable [varName, numValue]"),
            BlockSignature("increaseInt", " ", "", "%m.varInt increase 1", 0xFFEE7D16.toInt(), "Variable", "Increment number variable by 1 [varName]"),
            BlockSignature("decreaseInt", " ", "", "%m.varInt decrease 1", 0xFFEE7D16.toInt(), "Variable", "Decrement number variable by 1 [varName]"),
            BlockSignature("setVarString", " ", "", "set %m.varStr to %s", 0xFFEE7D16.toInt(), "Variable", "Set string variable [varName, strValue]"),
            BlockSignature("getArg", "s", "", "%s", 0xFF5947B1.toInt(), "MoreBlock/Event", "Read event/MoreBlock argument (set spec to arg name)"),
            BlockSignature("definedFunc", " ", "", "%s", 0xFF8A55D7.toInt(), "MoreBlock", "Call a MoreBlock defined in this Activity"),

            // Map blocks
            BlockSignature("mapCreateNew", " ", "", "%m.varMap create new map", 0xFF009688.toInt(), "Map", "Initialize new HashMap [mapVarName]"),
            BlockSignature("mapPutValue", " ", "", "%m.varMap put key %s value %s", 0xFF009688.toInt(), "Map", "Put key-value into Map [mapVarName, key, value]"),
            BlockSignature("mapGetValue", "s", "", "%m.varMap get key %s", 0xFF009688.toInt(), "Map", "Get string value by key from Map [mapVarName, key]"),
            BlockSignature("mapContainKey", "b", "", "%m.varMap contains key %s", 0xFF009688.toInt(), "Map", "Check if Map contains key [mapVarName, key]"),
            BlockSignature("mapRemoveKey", " ", "", "%m.varMap remove key %s", 0xFF009688.toInt(), "Map", "Remove key from Map [mapVarName, key]"),
            BlockSignature("mapSize", "d", "", "%m.varMap size", 0xFF009688.toInt(), "Map", "Get number of entries in Map [mapVarName]"),
            BlockSignature("mapClear", " ", "", "%m.varMap clear", 0xFF009688.toInt(), "Map", "Clear all entries in Map [mapVarName]"),
            BlockSignature("mapIsEmpty", "b", "", "%m.varMap is empty", 0xFF009688.toInt(), "Map", "Check if Map is empty [mapVarName]"),
            BlockSignature("strToMap", " ", "", "Gson String %s to Map %m.varMap", 0xFF009688.toInt(), "Map", "Parse JSON object string into Map [jsonStr, mapVarName]"),
            BlockSignature("mapToStr", "s", "", "Gson Map %m.varMap to String", 0xFF009688.toInt(), "Map", "Serialize Map to JSON string [mapVarName]"),

            // List blocks
            BlockSignature("addListInt", " ", "", "add %d to %m.listInt", 0xFFCC5B22.toInt(), "List", "Add number to ListInt [number, listName]"),
            BlockSignature("insertListInt", " ", "", "insert %d at %d to %m.listInt", 0xFFCC5B22.toInt(), "List", "Insert number at index in ListInt [number, index, listName]"),
            BlockSignature("getAtListInt", "d", "", "get at %d of %m.listInt", 0xFFCC5B22.toInt(), "List", "Get number at index in ListInt [index, listName]"),
            BlockSignature("indexListInt", "d", "", "index %d in %m.listInt", 0xFFCC5B22.toInt(), "List", "Find index of number in ListInt [number, listName]"),
            BlockSignature("containListInt", "b", "", "%m.listInt contains %d", 0xFFCC5B22.toInt(), "List", "Check if ListInt contains number [listName, number]"),
            BlockSignature("addListStr", " ", "", "add %s to %m.listStr", 0xFFCC5B22.toInt(), "List", "Add string to ListString [str, listName]"),
            BlockSignature("insertListStr", " ", "", "insert %s at %d to %m.listStr", 0xFFCC5B22.toInt(), "List", "Insert string at index in ListString [str, index, listName]"),
            BlockSignature("getAtListStr", "s", "", "get at %d of %m.listStr", 0xFFCC5B22.toInt(), "List", "Get string at index in ListString [index, listName]"),
            BlockSignature("indexListStr", "d", "", "index %s in %m.listStr", 0xFFCC5B22.toInt(), "List", "Find index of string in ListString [str, listName]"),
            BlockSignature("containListStr", "b", "", "%m.listStr contains %s", 0xFFCC5B22.toInt(), "List", "Check if ListString contains string [listName, str]"),
            BlockSignature("deleteList", " ", "", "delete at %d of %m.list", 0xFFCC5B22.toInt(), "List", "Delete item at index of any List [index, listName]"),
            BlockSignature("lengthList", "d", "", "length of %m.list", 0xFFCC5B22.toInt(), "List", "Get size of any List [listName]"),
            BlockSignature("clearList", " ", "", "clear %m.list", 0xFFCC5B22.toInt(), "List", "Clear any List [listName]"),
            BlockSignature("addListMap", " ", "", "add key %s value %s to %m.listMap", 0xFFCC5B22.toInt(), "ListMap", "Add new entry with key/value to ListMap [key, value, listMapName]"),
            BlockSignature("insertListMap", " ", "", "insert at %d key %s value %s to %m.listMap", 0xFFCC5B22.toInt(), "ListMap", "Insert entry at index in ListMap [index, key, value, listMapName]"),
            BlockSignature("setListMap", " ", "", "set value at %d key %s value %s to %m.listMap", 0xFFCC5B22.toInt(), "ListMap", "Update key/value at index in ListMap [index, key, value, listMapName]"),
            BlockSignature("getValueListMap", "s", "", "get value at %d key %s of %m.listMap", 0xFFCC5B22.toInt(), "ListMap", "Get value at index and key in ListMap [index, key, listMapName]"),
            BlockSignature("containKeyListMap", "b", "", "at %d of %m.listMap contains key %s", 0xFFCC5B22.toInt(), "ListMap", "Check if map at index in ListMap contains key [index, listMapName, key]"),
            BlockSignature("addNewMapToList", " ", "", "add %m.varMap to %m.listMap", 0xFFCC5B22.toInt(), "ListMap", "Add Map variable to ListMap [mapVarName, listMapName]"),
            BlockSignature("getMapFromList", " ", "", "get map at %d of %m.listMap to %m.varMap", 0xFFCC5B22.toInt(), "ListMap", "Copy map at index of ListMap into Map variable [index, listMapName, mapVarName]"),
            BlockSignature("strToListMap", " ", "", "Gson String %s to ListMap %m.listMap", 0xFFCC5B22.toInt(), "ListMap", "Parse JSON array string into ListMap [jsonStr, listMapName]"),
            BlockSignature("listMapToStr", "s", "", "Gson ListMap %m.listMap to String", 0xFFCC5B22.toInt(), "ListMap", "Serialize ListMap to JSON array string [listMapName]"),

            // Control Flow
            BlockSignature("repeat", "c", "", "repeat %d", 0xFFE1A92A.toInt(), "Control", "Repeat subStack1 N times [count]"),
            BlockSignature("forever", "c", "", "forever", 0xFFE1A92A.toInt(), "Control", "Infinite loop (while(true))"),
            BlockSignature("break", "f", "", "break", 0xFFE1A92A.toInt(), "Control", "Break out of loop"),
            BlockSignature("continue", "f", "", "continue", 0xFFE1A92A.toInt(), "Control", "Continue to next iteration of loop"),
            BlockSignature("if", "c", "", "if %b then", 0xFFE1A92A.toInt(), "Control", "If condition is true, run subStack1 [boolExpr]"),
            BlockSignature("ifElse", "e", "", "if %b then", 0xFFE1A92A.toInt(), "Control", "If condition is true run subStack1, else run subStack2 [boolExpr]"),
            BlockSignature("tryCatch", "e", "", "try", 0xFFE1A92A.toInt(), "Control", "Try executing subStack1, catch Exception in subStack2"),

            // Operators & Math & String
            BlockSignature("true", "b", "", "true", 0xFF5CB722.toInt(), "Operator", "Boolean literal true"),
            BlockSignature("false", "b", "", "false", 0xFF5CB722.toInt(), "Operator", "Boolean literal false"),
            BlockSignature("<", "b", "", "%d < %d", 0xFF5CB722.toInt(), "Operator", "Less than [numA, numB]"),
            BlockSignature("=", "b", "", "%d = %d", 0xFF5CB722.toInt(), "Operator", "Numeric equality [numA, numB]"),
            BlockSignature(">", "b", "", "%d > %d", 0xFF5CB722.toInt(), "Operator", "Greater than [numA, numB]"),
            BlockSignature("&&", "b", "", "%b and %b", 0xFF5CB722.toInt(), "Operator", "Logical AND [boolA, boolB]"),
            BlockSignature("||", "b", "", "%b or %b", 0xFF5CB722.toInt(), "Operator", "Logical OR [boolA, boolB]"),
            BlockSignature("not", "b", "", "not %b", 0xFF5CB722.toInt(), "Operator", "Logical NOT [bool]"),
            BlockSignature("+", "d", "", "%d + %d", 0xFF5CB722.toInt(), "Operator", "Addition [numA, numB]"),
            BlockSignature("-", "d", "", "%d - %d", 0xFF5CB722.toInt(), "Operator", "Subtraction [numA, numB]"),
            BlockSignature("*", "d", "", "%d * %d", 0xFF5CB722.toInt(), "Operator", "Multiplication [numA, numB]"),
            BlockSignature("/", "d", "", "%d / %d", 0xFF5CB722.toInt(), "Operator", "Division [numA, numB]"),
            BlockSignature("%", "d", "", "%d % %d", 0xFF5CB722.toInt(), "Operator", "Modulo [numA, numB]"),
            BlockSignature("random", "d", "", "pick random %d to %d", 0xFF5CB722.toInt(), "Operator", "Random integer between min and max [min, max]"),
            BlockSignature("stringLength", "d", "", "length of %s", 0xFF5CB722.toInt(), "Operator", "Length of string [str]"),
            BlockSignature("stringJoin", "s", "", "join %s and %s", 0xFF5CB722.toInt(), "Operator", "Concatenate two strings [strA, strB]"),
            BlockSignature("stringIndex", "d", "", "index of %s in %s", 0xFF5CB722.toInt(), "Operator", "Index of substring in string [sub, str]"),
            BlockSignature("stringSub", "s", "", "substring %s from %d to %d", 0xFF5CB722.toInt(), "Operator", "Substring [str, start, end]"),
            BlockSignature("stringEquals", "b", "", "%s equals %s", 0xFF5CB722.toInt(), "Operator", "String equality [strA, strB]"),
            BlockSignature("stringContains", "b", "", "%s contains %s", 0xFF5CB722.toInt(), "Operator", "Check if string contains substring [str, sub]"),
            BlockSignature("stringReplace", "s", "", "%s replace all %s with %s", 0xFF5CB722.toInt(), "Operator", "Replace all occurrences in string [str, target, replacement]"),
            BlockSignature("toNumber", "d", "", "toNumber %s", 0xFF5CB722.toInt(), "Operator", "Parse string to double [str]"),
            BlockSignature("toString", "s", "", "toString %d without decimal", 0xFF5CB722.toInt(), "Operator", "Format number as integer string [num]"),
            BlockSignature("toStringWithDecimal", "s", "", "toString %d with decimal %s", 0xFF5CB722.toInt(), "Operator", "Format number with decimal pattern [num, pattern]"),
            BlockSignature("trim", "s", "", "trim %s", 0xFF5CB722.toInt(), "Operator", "Trim whitespace [str]"),
            BlockSignature("toUpperCase", "s", "", "toUpperCase %s", 0xFF5CB722.toInt(), "Operator", "Convert string to uppercase [str]"),
            BlockSignature("toLowerCase", "s", "", "toLowerCase %s", 0xFF5CB722.toInt(), "Operator", "Convert string to lowercase [str]"),
            BlockSignature("addSourceDirectly", " ", "", "add source directly %s.inputOnly", 0xFF5CB722.toInt(), "Operator", "Inject raw Java code directly into this event/method [javaCode]"),

            // Views & Widgets
            BlockSignature("setEnable", " ", "", "%m.view setEnabled %b", 0xFF4A6CD4.toInt(), "View", "Enable or disable view [viewId, enabledBool]"),
            BlockSignature("getEnable", "b", "", "%m.view getEnabled", 0xFF4A6CD4.toInt(), "View", "Check if view is enabled [viewId]"),
            BlockSignature("setVisible", " ", "", "%m.view setVisible %m.visible", 0xFF4A6CD4.toInt(), "View", "Set visibility of view [viewId, VISIBLE|INVISIBLE|GONE]"),
            BlockSignature("setClickable", " ", "", "%m.view setClickable %b", 0xFF4A6CD4.toInt(), "View", "Set view clickable [viewId, clickableBool]"),
            BlockSignature("setAlpha", " ", "", "%m.view setAlpha %d", 0xFF4A6CD4.toInt(), "View", "Set view alpha (0.0 to 1.0) [viewId, alpha]"),
            BlockSignature("getAlpha", "d", "", "%m.view getAlpha", 0xFF4A6CD4.toInt(), "View", "Get view alpha [viewId]"),
            BlockSignature("setBgColor", " ", "", "%m.view setBackgroundColor %c", 0xFF4A6CD4.toInt(), "View", "Set background color (e.g. 0xFF2196F3) [viewId, colorHex]"),
            BlockSignature("setBgResource", " ", "", "%m.view setBackgroundResource %m.resource_bg", 0xFF4A6CD4.toInt(), "View", "Set background drawable resource [viewId, drawableName]"),
            BlockSignature("setText", " ", "", "%m.textview setText %s", 0xFF4A6CD4.toInt(), "View", "Set text of TextView/Button/EditText/CheckBox/Switch [viewId, text]"),
            BlockSignature("getText", "s", "", "%m.textview getText", 0xFF4A6CD4.toInt(), "View", "Get text of TextView/Button/EditText [viewId]"),
            BlockSignature("setTextColor", " ", "", "%m.textview setTextColor %c", 0xFF4A6CD4.toInt(), "View", "Set text color (e.g. 0xFFFFFFFF) [viewId, colorHex]"),
            BlockSignature("setTextSize", " ", "", "%m.textview setTextSize %d", 0xFF4A6CD4.toInt(), "View", "Set text size in sp [viewId, size]"),
            BlockSignature("edittextSetHint", " ", "", "%m.edittext setHint %s", 0xFF4A6CD4.toInt(), "View", "Set hint text on EditText [viewId, hint]"),
            BlockSignature("edittextSetHintColor", " ", "", "%m.edittext setHintTextColor %c", 0xFF4A6CD4.toInt(), "View", "Set hint text color on EditText [viewId, colorHex]"),
            BlockSignature("setImage", " ", "", "%m.imageview setImage %m.resource", 0xFF4A6CD4.toInt(), "View", "Set ImageView drawable resource [viewId, resourceName]"),
            BlockSignature("setImageFilePath", " ", "", "%m.imageview setImageFromFilePath %s", 0xFF4A6CD4.toInt(), "View", "Load image into ImageView from file path [viewId, filePath]"),
            BlockSignature("setImageUrl", " ", "", "%m.imageview setImageFromUrl %s", 0xFF4A6CD4.toInt(), "View", "Load image into ImageView from URL using Glide [viewId, url]"),
            BlockSignature("setChecked", " ", "", "%m.compoundButton setChecked %b", 0xFF4A6CD4.toInt(), "View", "Set checked state of CheckBox/Switch [viewId, checkedBool]"),
            BlockSignature("getChecked", "b", "", "%m.compoundButton isChecked", 0xFF4A6CD4.toInt(), "View", "Get checked state of CheckBox/Switch [viewId]"),
            BlockSignature("listSetData", " ", "", "%m.listview setListViewData %m.listStr", 0xFF4A6CD4.toInt(), "View", "Bind ListString to ListView [listViewId, listStrName]"),
            BlockSignature("listSetCustomViewData", " ", "", "%m.listview setListCustomViewData %m.listMap", 0xFF4A6CD4.toInt(), "View", "Bind ListMap to ListView with CustomView [listViewId, listMapName]"),
            BlockSignature("listRefresh", " ", "", "%m.listview refreshData", 0xFF4A6CD4.toInt(), "View", "Notify adapter data changed on ListView [listViewId]"),
            BlockSignature("recyclerSetCustomViewData", " ", "", "%m.recyclerview setRecyclerCustomViewData %m.listMap", 0xFF4A6CD4.toInt(), "View", "Bind ListMap to RecyclerView [recyclerViewId, listMapName]"),
            BlockSignature("spnSetData", " ", "", "%m.spinner setSpinnerData %m.listStr", 0xFF4A6CD4.toInt(), "View", "Bind ListString to Spinner [spinnerId, listStrName]"),
            BlockSignature("spnRefresh", " ", "", "%m.spinner refreshData", 0xFF4A6CD4.toInt(), "View", "Refresh Spinner adapter [spinnerId]"),
            BlockSignature("spnSetSelection", " ", "", "%m.spinner setSelection %d", 0xFF4A6CD4.toInt(), "View", "Set Spinner selected index [spinnerId, index]"),
            BlockSignature("spnGetSelection", "d", "", "%m.spinner getSelection", 0xFF4A6CD4.toInt(), "View", "Get Spinner selected index [spinnerId]"),
            BlockSignature("webViewLoadUrl", " ", "", "%m.webview loadUrl %s", 0xFF4A6CD4.toInt(), "View", "Load URL in WebView [webViewId, url]"),
            BlockSignature("webViewGetUrl", "s", "", "%m.webview getUrl", 0xFF4A6CD4.toInt(), "View", "Get current URL from WebView [webViewId]"),
            BlockSignature("webViewCanGoBack", "b", "", "%m.webview canGoBack", 0xFF4A6CD4.toInt(), "View", "Check if WebView can go back [webViewId]"),
            BlockSignature("webViewGoBack", " ", "", "%m.webview goBack", 0xFF4A6CD4.toInt(), "View", "Navigate back in WebView [webViewId]"),
            BlockSignature("progressBarSetProgress", " ", "", "%m.progressbar setProgress %d", 0xFF4A6CD4.toInt(), "View", "Set ProgressBar progress [progressBarId, value]"),
            BlockSignature("seekBarSetProgress", " ", "", "%m.seekbar setProgress %d", 0xFF4A6CD4.toInt(), "View", "Set SeekBar progress [seekBarId, value]"),
            BlockSignature("seekBarGetProgress", "d", "", "%m.seekbar getProgress", 0xFF4A6CD4.toInt(), "View", "Get SeekBar progress [seekBarId]"),

            // Components & Activity
            BlockSignature("doToast", " ", "", "Toast %s", 0xFF2CA5E2.toInt(), "Component", "Show Toast message on screen [message]"),
            BlockSignature("setTitle", " ", "", "Activity setTitle %s", 0xFF2CA5E2.toInt(), "Component", "Set Activity toolbar title [title]"),
            BlockSignature("finishActivity", "f", "", "finish Activity", 0xFF2CA5E2.toInt(), "Component", "Finish current Activity"),
            BlockSignature("intentSetAction", " ", "", "%m.intent setAction %m.intentAction", 0xFF2CA5E2.toInt(), "Component", "Set Intent action (ACTION_VIEW, ACTION_DIAL, ACTION_CALL) [intentId, action]"),
            BlockSignature("intentSetData", " ", "", "%m.intent setData %s", 0xFF2CA5E2.toInt(), "Component", "Set Intent data URI string [intentId, uriStr]"),
            BlockSignature("intentSetScreen", " ", "", "%m.intent setScreen %m.activity", 0xFF2CA5E2.toInt(), "Component", "Set target Activity class for Intent [intentId, ActivityName]"),
            BlockSignature("intentPutExtra", " ", "", "%m.intent putExtra key %s value %s", 0xFF2CA5E2.toInt(), "Component", "Put string extra into Intent [intentId, key, value]"),
            BlockSignature("intentGetString", "s", "", "Activity getExtra key %s", 0xFF2CA5E2.toInt(), "Component", "Get string extra from launching Intent [key]"),
            BlockSignature("intentSetFlags", " ", "", "%m.intent setFlags %m.intentFlags", 0xFF2CA5E2.toInt(), "Component", "Set Intent flags (FLAG_ACTIVITY_CLEAR_TOP, etc.) [intentId, flag]"),
            BlockSignature("startActivity", " ", "", "startActivity %m.intent", 0xFF2CA5E2.toInt(), "Component", "Launch Activity using Intent component [intentId]"),
            BlockSignature("fileGetData", "s", "", "%m.file getData key %s", 0xFF2CA5E2.toInt(), "Component", "Read string from SharedPreferences [fileComponentId, key]"),
            BlockSignature("fileSetData", " ", "", "%m.file setData key %s value %s", 0xFF2CA5E2.toInt(), "Component", "Save string to SharedPreferences [fileComponentId, key, value]"),
            BlockSignature("fileRemoveData", " ", "", "%m.file removeData key %s", 0xFF2CA5E2.toInt(), "Component", "Remove key from SharedPreferences [fileComponentId, key]"),
            BlockSignature("calendarGetNow", " ", "", "%m.calendar getNow", 0xFF2CA5E2.toInt(), "Component", "Set Calendar component to current time [calendarId]"),
            BlockSignature("calendarFormat", "s", "", "%m.calendar Format %s", 0xFF2CA5E2.toInt(), "Component", "Format Calendar date/time (e.g. yyyy-MM-dd HH:mm:ss) [calendarId, pattern]"),
            BlockSignature("vibratorAction", " ", "", "%m.vibrator vibrate for %d ms", 0xFF2CA5E2.toInt(), "Component", "Vibrate device for duration in ms [vibratorId, ms]"),
            BlockSignature("timerAfter", "c", "", "%m.timer after %d ms", 0xFF2CA5E2.toInt(), "Component", "Run subStack1 after delay in ms [timerId, delayMs]"),
            BlockSignature("timerEvery", "c", "", "%m.timer after %d ms for every %d ms", 0xFF2CA5E2.toInt(), "Component", "Run subStack1 repeatedly [timerId, delayMs, periodMs]"),
            BlockSignature("timerCancel", " ", "", "%m.timer cancel", 0xFF2CA5E2.toInt(), "Component", "Cancel TimerTask [timerId]"),
            BlockSignature("dialogSetTitle", " ", "", "%m.dialog setTitle %s", 0xFF2CA5E2.toInt(), "Component", "Set AlertDialog title [dialogId, title]"),
            BlockSignature("dialogSetMessage", " ", "", "%m.dialog setMessage %s", 0xFF2CA5E2.toInt(), "Component", "Set AlertDialog message [dialogId, message]"),
            BlockSignature("dialogOkButton", "c", "", "%m.dialog OK Button %s clicked", 0xFF2CA5E2.toInt(), "Component", "Set AlertDialog positive button text and click handler in subStack1 [dialogId, btnText]"),
            BlockSignature("dialogCancelButton", "c", "", "%m.dialog Cancel Button %s clicked", 0xFF2CA5E2.toInt(), "Component", "Set AlertDialog negative button text and click handler in subStack1 [dialogId, btnText]"),
            BlockSignature("dialogNeutralButton", "c", "", "%m.dialog Neutral Button %s clicked", 0xFF2CA5E2.toInt(), "Component", "Set AlertDialog neutral button text and click handler in subStack1 [dialogId, btnText]"),
            BlockSignature("dialogShow", " ", "", "%m.dialog show", 0xFF2CA5E2.toInt(), "Component", "Display AlertDialog [dialogId]"),
            BlockSignature("requestNetworkSetParams", " ", "", "%m.requestnetwork setParams %m.varMap to %m.requestnetworkReqType", 0xFF2CA5E2.toInt(), "Component", "Set RequestNetwork parameters [reqNetId, mapVarName, REQUEST_PARAM|REQUEST_BODY]"),
            BlockSignature("requestNetworkSetHeaders", " ", "", "%m.requestnetwork setHeaders %m.varMap", 0xFF2CA5E2.toInt(), "Component", "Set RequestNetwork HTTP headers [reqNetId, mapVarName]"),
            BlockSignature("requestNetworkStartRequestNetwork", " ", "", "%m.requestnetwork startRequestNetwork %m.requestnetworkMethod to url %s with tag %s", 0xFF2CA5E2.toInt(), "Component", "Start HTTP request [reqNetId, GET|POST|PUT|DELETE, url, tag]"),

            // FileUtil Storage blocks
            BlockSignature("fileutilread", "s", "", "readFile path %s", 0xFFA1887F.toInt(), "File", "Read string content from file path [filePath]"),
            BlockSignature("fileutilwrite", " ", "", "writeFile path %s data %s", 0xFFA1887F.toInt(), "File", "Write string content to file path [filePath, content]"),
            BlockSignature("fileutilcopy", " ", "", "copyFile source %s dest %s", 0xFFA1887F.toInt(), "File", "Copy file from source to destination [srcPath, destPath]"),
            BlockSignature("fileutildelete", " ", "", "deleteFile path %s", 0xFFA1887F.toInt(), "File", "Delete file or directory at path [path]"),
            BlockSignature("fileutilisexist", "b", "", "isExistFile path %s", 0xFFA1887F.toInt(), "File", "Check if file/directory exists [path]"),
            BlockSignature("fileutilmakedir", " ", "", "makeDir path %s", 0xFFA1887F.toInt(), "File", "Create directory at path [path]"),
            BlockSignature("fileutillistdir", " ", "", "listDir path %s to %m.listStr", 0xFFA1887F.toInt(), "File", "List files in directory into ListString [path, listStrName]"),
            BlockSignature("getExternalStorageDir", "s", "", "getExternalStorageDir", 0xFFA1887F.toInt(), "File", "Get external storage root path"),
            BlockSignature("getPackageDataDir", "s", "", "getPackageDataDir", 0xFFA1887F.toInt(), "File", "Get app private data directory path")
        )
        list.associateBy { it.opCode }
    }

    @Volatile
    private var dynamicBlocksCache: Map<String, BlockSignature>? = null

    private fun getAllBlocks(): Map<String, BlockSignature> {
        val cached = dynamicBlocksCache
        if (cached != null) return cached

        val combined = LinkedHashMap<String, BlockSignature>(coreBlocks)
        try {
            val extraList = ArrayList<HashMap<String, Any>>()
            BlocksHandler.builtInBlocks(extraList)
            for (map in extraList) {
                val opCode = map["name"]?.toString()?.takeIf { it.isNotEmpty() } ?: continue
                if (combined.containsKey(opCode)) continue
                val type = map["type"]?.toString()?.ifEmpty { " " } ?: " "
                val typeName = map["typeName"]?.toString().orEmpty()
                val spec = map["spec"]?.toString().orEmpty()
                val colorStr = map["color"]?.toString().orEmpty()
                val colorInt = parseColorSafe(colorStr, kq.a(opCode, type))
                combined[opCode] = BlockSignature(
                    opCode = opCode,
                    type = type,
                    typeName = typeName,
                    spec = spec,
                    color = colorInt,
                    category = "SketchwarePro Extra"
                )
            }
        } catch (_: Throwable) {
        }

        try {
            val customList = ExtraBlockFile.getExtraBlockData()
            for (map in customList) {
                val opCode = map["name"]?.toString()?.takeIf { it.isNotEmpty() } ?: continue
                if (combined.containsKey(opCode)) continue
                val type = map["type"]?.toString()?.ifEmpty { " " } ?: " "
                val typeName = map["typeName"]?.toString().orEmpty()
                val spec = map["spec"]?.toString().orEmpty()
                val colorStr = map["color"]?.toString().orEmpty()
                val colorInt = parseColorSafe(colorStr, 0xFF4A6CD4.toInt())
                combined[opCode] = BlockSignature(
                    opCode = opCode,
                    type = type,
                    typeName = typeName,
                    spec = spec,
                    color = colorInt,
                    category = "Custom Block"
                )
            }
        } catch (_: Throwable) {
        }

        dynamicBlocksCache = combined
        return combined
    }

    fun resolve(context: Context?, opCode: String, explicitType: String? = null, explicitSpec: String? = null): BlockSignature {
        val all = getAllBlocks()
        val found = all[opCode]
        if (found != null) {
            return found.copy(
                type = explicitType?.takeIf { it.isNotEmpty() } ?: found.type,
                spec = explicitSpec?.takeIf { it.isNotEmpty() } ?: found.spec
            )
        }

        val info = try {
            BlockLoader.getBlockInfo(opCode)
        } catch (_: Throwable) {
            null
        }
        val inferredType = explicitType?.takeIf { it.isNotEmpty() } ?: " "
        val colorFromKq = try {
            kq.a(opCode, inferredType)
        } catch (_: Throwable) {
            0xFF4A6CD4.toInt()
        }

        val resolvedSpec = when {
            !explicitSpec.isNullOrEmpty() -> explicitSpec
            info != null && !info.isMissing && !info.spec.isNullOrEmpty() -> info.spec
            context != null -> try {
                xB.b().a(context, opCode).takeIf { it.isNotEmpty() } ?: opCode
            } catch (_: Throwable) {
                opCode
            }
            else -> opCode
        }

        val resolvedColor = when {
            info != null && !info.isMissing && info.color != 0 -> info.color
            colorFromKq != 0 -> colorFromKq
            else -> 0xFF4A6CD4.toInt()
        }

        return BlockSignature(
            opCode = opCode,
            type = inferredType,
            typeName = "",
            spec = resolvedSpec,
            color = resolvedColor,
            category = "Dynamic"
        )
    }

    fun search(query: String, limit: Int = 40): List<BlockSignature> {
        val all = getAllBlocks().values
        val q = query.trim().lowercase()
        if (q.isEmpty()) {
            return all.take(limit)
        }
        val tokens = q.split(Regex("\\s+")).filter { it.isNotEmpty() }
        return all.filter { sig ->
            val hay = "${sig.opCode} ${sig.spec} ${sig.category} ${sig.description}".lowercase()
            tokens.all { hay.contains(it) }
        }.take(limit)
    }

    private fun parseColorSafe(colorStr: String, fallback: Int): Int {
        if (colorStr.isBlank()) return fallback
        return try {
            Color.parseColor(colorStr)
        } catch (_: Exception) {
            fallback
        }
    }
}
