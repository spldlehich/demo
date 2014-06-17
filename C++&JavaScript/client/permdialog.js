var DeviceGroupListModel = Dispatcher.require("dispatch/deviceGroupList.js").DeviceGroupListModel;
var signal = Dispatcher.require("core/businessobject.js").signal;

function PermDialog(ctx) {
	var ui = Dispatcher.widget("dispatch/permdialog.ui");
	BaseWidget.call(this, ui, ctx);
    this.$groupTree = this.findChild('groupTree');
	this.$btnBox = this.findChild('buttonBox');
	this.groupChanged = new signal();
}
PermDialog.inheritsFrom(BaseWidget);

PermDialog.prototype.init = function(grouplink) {
	this.user = { grouplink: grouplink||[] };
	this.getContext().$commit.basecompanyList.forEach(function(c) {
		//FIXME: Why to take first company and update then with actual one?
		if(!this.company || c.staticid === this.$staticid) {
			this.company = c;
		}
	}, this);

	//NOTE: load available group list
	this.$model = new DeviceGroupListModel(this.getContext());
	this.$model.setCompany(this.company);
	this.$model.load();

	//NOTE: set up group tree
	this.$groupTree.configureScriptModel({
		childrenProperty: "children",
		columns: [{
			textProperty: "title",
			flagsProperty: "flags",
			checkedProperty: "checked"
		}]
	});
	if(this.$model.groupRoot && this.$model.groupRoot.length > 0) {
		this.$groupTree.layoutAboutToBeChanged();
		//NOTE: do not display fake top level element
		this.$groupTree.setScriptItems(this.$model.groupRoot[0].children);
		this.$groupTree.layoutChanged();
	}
	this.$groupTree.toggled.connect(this, this.onToggled);
	this.$groupTree.expandAll();

	//NOTE: load and check current group links
	this.updateChecked(this.$model.groupRoot);

	//NOTE: successfull finish
	this.$btnBox.accepted.connect(this, this.on_$btnBox_accepted);
}

PermDialog.prototype.on_$btnBox_accepted = function () {
	//NOTE: clean the user's group link list
	this.user.grouplink = [];
	//NOTE: push all new items to user's group link list
	this.saveChecked(this.$model.groupRoot);
	this.groupChanged.emit(this.user.grouplink);
	this.close();
}

PermDialog.prototype.updateChecked = function(children) {
	this.$groupTree.layoutAboutToBeChanged();
	if (children) {
		children.forEach(function (item) {
			//NOTE: only items related to groups can be checkable.
			if(item.staticid) {
				item.flags = {checkable: true};
				this.updateChecked(item.children);
				this.user.grouplink.forEach(function(lnk){
					if (lnk === item.staticid) {
						item.checked = true;
						this.removeChildrenNode(item.children);
					}
				}, this);
			} else {
				//NOTE: not chackable if not a group
				item.flags = {checkable: false};
				this.updateChecked(item.children);
			}
		}, this);
	}
	this.$groupTree.layoutChanged();
};

PermDialog.prototype.saveChecked = function(children) {
	//NOTE: store top level checked items
	if (children) {
		children.forEach(function (item) {
			if (item.checked) {
				this.user.grouplink.push(item.staticid);
			} else {
				this.saveChecked(item.children);
			}
		}, this);
	}
};

PermDialog.prototype.onToggled = function(value, item) {
	this.$groupTree.layoutAboutToBeChanged();
	if ((item.checked === false) || (!item.checked)) {
		this.removeChildrenNode(item.children);
	} else {
		this.addChildrenNode(item.children);
	}
	item.checked = !item.checked;
	this.$groupTree.layoutChanged();
};

PermDialog.prototype.removeChildrenNode = function(children) {
	if (children) {
		children.forEach(function (item) {
			item.checked = false;
			item.flags = {checkable: false};
			this.removeChildrenNode(item.children);
		}, this);
	}
};

PermDialog.prototype.addChildrenNode = function(children) {
	if (children) {
		children.forEach(function (item) {
			item.checked = false;
			item.flags = {checkable: true};
			this.addChildrenNode(item.children);
		}, this);
	}
};

exports.PermDialog = PermDialog;
