Dispatcher.registerTranslation("localeNames");
function LanguageDialog(ui, ctx) {
	var ui = Dispatcher.widget("dispatch/languagedialog.ui");
	BaseWidget.call(this, ui, ctx);

	this.$comboBox = this.findChild('comboBox');
	this.$btnBox = this.findChild('buttonBox');
}
LanguageDialog.inheritsFrom(BaseWidget);

LanguageDialog.prototype.init = function() {
	this.$localeList = Dispatcher.getLocaleList();
	var arrShow = [ ];
	var currentLanguageIndex = 0;
	this.$localeList.forEach(function(value, index) {
	  arrShow.push(qsTr(value));
	  if (uiSettings.language == value)
	    currentLanguageIndex = index;
	});
	this.$comboBox.clear();
	this.$comboBox.configureScriptValueModel({});
	this.$comboBox.setScriptItems(arrShow);
	this.$comboBox.setCurrentIndex(currentLanguageIndex);
	this.$btnBox.accepted.connect(this, this.on_$btnBox_accepted);
};

LanguageDialog.prototype.on_$btnBox_accepted = function() {
	if (this.$comboBox.currentIndex >= 0){
		uiSettings.language = this.$localeList[this.$comboBox.currentIndex];
		this.$ui.enabled = false;
		Dispatcher["localeChanged(int)"].connect(this, this.close);
		Dispatcher.setTimeout(function() {
			Dispatcher.localize(uiSettings.language);
		}, 0);
	}
};

LanguageDialog.prototype.cleanUp = function() {
	try {
		Dispatcher["localeChanged(int)"].disconnect(this, this.close);
		this.$btnBox.accepted.disconnect(this, this.on_$btnBox_accepted);
	} catch (e) { }
	BaseWidget.prototype.cleanUp.call(this);
};

exports.LanguageDialog = LanguageDialog;
