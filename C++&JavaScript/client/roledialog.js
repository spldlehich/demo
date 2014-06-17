function RoleDialog(ctx) {
	var ui = Dispatcher.widget("dispatch/roledialog.ui");
	BaseWidget.call(this, ui, ctx);
	this.$newRole = this.findChild('newRole');
	this.$permissionTree = this.findChild('permissionTree');

	this.$btnBox = this.findChild('buttonBox');
	this.$btnBox.accepted.connect(this, this.on_$btnBox_accepted);

	this.$roleView = this.findChild('roleView');
	this.$roleView.selected.connect(this, this.on_$roleView_selected);

	this.$addButton = this.findChild('addButton');
	this.$addButton.clicked.connect(this, this.on_$addButton_clicked);

	this.$removeButton = this.findChild('removeButton');
	this.$removeButton.clicked.connect(this, this.on_$removeButton_clicked);

	this.$licenseSelector = this.findChild('licenseComboBox');
	this.$licenseSelector.configureScriptValueModel({
		parentProperty: "parent_roleDialog_$licenseSelector"
	});
	this.$roleListColumns = [
		{
			headerText: qsTr("Company"),
			textProperty: "company"
		}
		, {
			headerText: qsTr("Role name"),
			textProperty: "name"
		}
	];
	this.$roleView.configureScriptModel({
		columns: this.$roleListColumns,
		parentProperty: "parent_roleDialog_$roleView"
	});
	this.$roleView.header.setSectionStretch(0, false);
	this.$roleView.header.setSectionStretch(1, true);
	this.$roleView.modelReset.connect(this, this.on_$roleView_modelReset);

	this.$permissionTree.configureScriptModel({
		childrenProperty: "children",
		columns: [{
			textProperty: "text",
			flagsProperty: "flags",
			checkedProperty: "checked"
		}]
	});

	var admPerm = [{ // TODO: magick numbers
		perm: ["group"],
		mask: 1,
		text: qsTr("view group"),
		flags: {checkable:true}
	}, {
		perm: ["group"],
		mask: 4 | 8,
		text: qsTr("edit groups"),
		flags: {checkable:true}
	}, {
		perm: ["group"],
		mask: 2,
		text: qsTr("manage group permission"),
		flags: {checkable:true}
	}, {
		perm: ["license"],
		mask: 1,
		text: qsTr("view licences"),
		flags: {checkable:true}
	}, {
		perm: ["license"],
		mask: 4 | 8,
		text: qsTr("manage licenses"),
		flags: {checkable:true}
	}];
	admPerm = admPerm.concat([{
		perm: ["user", "role", "permissionrole", "grouplink"],
		mask: 1,
		text: qsTr("view users"),
		flags: {checkable:true}
	}, {
		perm: ["user", "role", "permissionrole", "grouplink"],
		mask: 2 | 4 | 8,
		text: qsTr("manage users"),
		flags: {checkable:true}
	}]);
	this.permTree = [{
		text: qsTr("Devices"),
		children: [{
			perm: ["device"],
			mask: 1,
			text: qsTr("view"),
			flags: {checkable:true}
		}, {
			perm: ["device"],
			mask: 4 | 8,
			text: qsTr("edit"),
			flags: {checkable:true}
		}]
	}, {
		text: qsTr("Places, fences and others"),
		children: [{
			perm: ["place", "fence", "reporttemplate", "fencedevice",
				"trip", "tripschedule", "friendsmessage",
				"notification", "notification_device", "notification_exclude_fence"],
			mask: 1,
			text: qsTr("view"),
			flags: {checkable:true}
		}, {
			perm: ["place", "fence", "reporttemplate", "fencedevice",
				"trip", "tripschedule", "friendsmessage",
				"notification", "notification_device", "notification_exclude_fence"],
			mask: 4 | 8,
			text: qsTr("edit"),
			flags: {checkable:true}
		}]
	}, {
		text: qsTr("Administrative"),
		children: admPerm
	}];

	this.$permissionTree.layoutAboutToBeChanged();
	this.$permissionTree.setScriptItems(this.permTree);
	this.$permissionTree.layoutChanged();
	this.$permissionTree.expandAll();

	this.$permissionTree.toggled.connect(this, this.on_$permissionTree_toggled);
}
RoleDialog.inheritsFrom(BaseWidget);

RoleDialog.prototype.setCompany = function(staticid) {
	this.getContext().resetToMaster();
	this.getContext().getCommit().basecompanyList.forEach(function(c) {
		if(c.staticid === this.$staticid) {
			this.company = c;
		}
	}, this);
};

RoleDialog.prototype.init = function(companyId) {
	this.$staticid = companyId;
	this.setCompany(this.$staticid)

	this.companyNameByRootGroup = {};
	this.company.licenseList.forEach(function(license) {
		this.companyNameByRootGroup[license.rootgroup] = license.title;
	}, this);

	this.$licenseSelector.modelColumnName = "title";
	this.$licenseSelector.model.layoutAboutToBeChanged();
	this.$licenseList = this.company.licenseList
		.map(function(item) {
			return {
				title: item.title,
				rootgroupid: item.rootgroup
			}
		}, this)
		.toArray();
	this.$licenseSelector.setScriptItems(this.$licenseList);
	this.$licenseSelector.model.layoutChanged();

	//NOTE: default selection is a company of current user
	//FIXME: avoid closure maybe
	var selectedLicenseId = this.getCurrentCompanyId();
	var selectedLicense = this.$licenseList
		.filter(function(item) { 
			return item.rootgroupid == selectedLicenseId 
		});
	this.$licenseSelector.selectItems(selectedLicense);

	this.updateChecked();

	this.$removeButton.enabled = false;
	this.load();
}

RoleDialog.prototype.load = function() {
	this.setCompany(this.$staticid)

	this.$permissionTree.setEnabled(false);
	this.$newRole.setEnabled(false);
	this.$licenseSelector.setEnabled(false);
	this.$removeButton.setEnabled(this.currentRole != null);

	if(this.company) {
		this.$roleList = this.company.roleList
			.map(function(role) {
				return {
					company : this.companyNameByRootGroup[role.parentid],
					name : role.name,
					role : role
				};
			}, this)
			.toArray();
		this.$roleView.layoutAboutToBeChanged();
		this.$roleView.setScriptItems(this.$roleList);
		this.$roleView.setSortingEnabled(true);
		this.$roleView.layoutChanged();
	}
};

RoleDialog.prototype.on_$roleView_modelReset = function() {
	this.$roleView.resizeRowsToContents();
	this.$roleView.resizeColumnsToContents();
	this.$roleView.header.stretch(false);
};

RoleDialog.prototype.on_$permissionTree_toggled = function(value, item) {
	if (!item.perm) {
		return;
	}
	if (item.checked === false) {
		item.perm.forEach(function(target) {
			this.addPermission(target, item.mask);
		}, this);
	} else {
		item.perm.forEach(function(target) {
			this.removePermission(target, item.mask);
		}, this);
	}
	item.checked = !item.checked;
};

RoleDialog.prototype.hasPerm = function(kind, mask) {
	var result = false;
	if (this.currentRole) {
		result = this.company.permissionRoleList
		.filter(function(perm) {
			return (
				(kind === perm.$data.kind) 
				&& (perm.$data.mask & mask) 
				&& (perm.$data.parentid === this.currentRole.staticid)
			);
		}, this)
		.getSize() > 0;
	}
	return result;
};

RoleDialog.prototype.updateChecked = function() {

	//FIXME: rid of local function declaration
	function partial() {
		var yes = false;
		var no = false;
		for (var i = 0; i < arguments.length; ++i) {
			if (arguments[i] === null) {
				yes = true;
				no = true;
			} else if (arguments[i]) {
				yes = true;
			} else {
				no = true;
			}
		}
		if (yes) {
			if (no) {
				return null;
			} else {
				return true;
			}
		} else {
			return false;
		}
	}

	this.mapEdit = {};
	this.$permissionTree.layoutAboutToBeChanged();
	this.permTree.forEach(function(type) {
		type.children.forEach(function(item) {
			var hasPermList = item.perm.map(function(kind) {
				var b = this.hasPerm(kind, item.mask);
				if (b) {
					this.addPermission(kind,item.mask);
				}
				return b;
			}, this);
			item.checked = partial.apply(this, hasPermList);
		}, this)
	}, this);
	this.$permissionTree.layoutChanged();
};

RoleDialog.prototype.removePermission = function(kind, mask) {
	if (kind in this.mapEdit) {
		this.mapEdit[kind] = this.mapEdit[kind] ^ mask;
	}
};

RoleDialog.prototype.addPermission = function (kind, mask) {
	if (kind in this.mapEdit)
		this.mapEdit[kind] = this.mapEdit[kind] | mask;
	else {
		this.mapEdit[kind] = mask;
	}
}

RoleDialog.prototype.on_$roleView_selected = function(selected, deselected) {
	this.isNew = false;
	if (selected.length === 1) {
		this.currentRole = selected[0].role;
		this.edit = true;
		this.$permissionTree.setEnabled(true);
		this.$newRole.setEnabled(true);
		this.$newRole.text = this.currentRole.name;
		this.$addButton.enabled = true;
		this.$removeButton.enabled = true;
		var roleLicense = this.$licenseList.filter(function(item) {
			return item.rootgroupid == this.currentRole.parentid;
		}, this);
		this.$licenseSelector.selectItems(roleLicense);
	} else {
		this.currentRole = null;
		this.edit = false;
		this.$permissionTree.setEnabled(false);
		this.$newRole.setEnabled(false);
		this.$newRole.text = "";
		this.$addButton.enabled = false;
		this.$removeButton.enabled = false;
	}
	this.updateChecked();
};

RoleDialog.prototype.on_$btnBox_accepted = function() {
	if (!Session.isConnected()) {
		return;
	}

	var role = this.$newRole.text;

	if(role && role.trim().length > 0){
		this.saveChanges();
	}
	else{
		var msg = Dispatcher.getMessageBox();
		msg.text = qsTr("Please, set the role name.");
		msg.exec();
	}
};

RoleDialog.prototype.on_$addButton_clicked = function(){
	this.$permissionTree.setEnabled(true);
	this.$newRole.setEnabled(true);
	this.$newRole.clear();
	this.$licenseSelector.setEnabled(true);
	this.mapEdit = {};
	this.edit = false;
	this.isNew = true;
	this.currentRole = null;
	this.$addButton.enabled = false;
	this.$removeButton.enabled = false;
	this.updateChecked();
};

RoleDialog.prototype.on_$removeButton_clicked = function() {
	if(!this.currentRole)
		return;

	var findUser = null;
	if(
		this.company.userList.toArray()
		.some(function(user) {
			if(user.srid === this.currentRole.staticid){
				findUser = user;
				return true;
			}
			return false;
		}, this)
	) {
		var msg = Dispatcher.getMessageBox();
		msg.text = qsTr("Remove", "Not possible to delete role \"%1\" because applied to user \"%2\".").arg(this.currentRole.name).arg(findUser.login);
		msg.exec();
	} 

	if (!findUser) {
		var c = this.getContext().getEditContext();
		var v = this.currentRole.startEditing(c);
		v.deleteObject();
		this.currentRole = null;
		c.saveChanges();

		this.mapEdit = {};
		this.edit = false;
		this.isNew = false;

		this.load();
		this.updateChecked();
		this.$permissionTree.setEnabled(false);
		this.$newRole.setEnabled(false);
	}
};

RoleDialog.prototype.getCurrentCompanyId = function() {
	var v = this.$licenseList[this.$licenseSelector.currentIndex];
	return v ? v.rootgroupid : this.company.currentLicense.rootgroup;
}

RoleDialog.prototype.createPermissions = function(ctx, roleId) {
	for (var obj in this.mapEdit) {
		if ((this.mapEdit[obj] != 'undefined') && (this.mapEdit[obj] > 0 )) {
			var permissionrole = ctx.createNew("permissionrole");
			permissionrole.kind = obj;
			permissionrole.mask = this.mapEdit[obj];
			permissionrole.parentid = roleId;
		}
	}
};

RoleDialog.prototype.saveChanges = function() {
	var c = this.getContext().getEditContext();
	if (this.edit && this.currentRole){
		this.isNew = false;

		//NOTE: delete existing permissions
		this.company.permissionRoleList.forEach(function(perm) {
			if (perm.$data.parentid === this.currentRole.staticid){
				var v = perm.startEditing(c);
				v.deleteObject();
			}
		}, this);
		//NOTE: create new permissions
		this.createPermissions(c, this.currentRole.staticid);
		var v = this.currentRole.startEditing(c);
		//NOTE: save role details
		v.name = this.$newRole.text;
		v.parentid = this.getCurrentCompanyId();

		c.saveChanges();
	} else if (this.isNew) {
		this.isNew = false;

		//NOTE: create new role
		var role = c.createNew("role");
		role.name = this.$newRole.text.trim();
		role.parentid = this.getCurrentCompanyId();
		//NOTE: create new permissions
		this.createPermissions(c, role.staticid);

		c.saveChanges();
	}
	this.$addButton.enabled = true;
	this.load(); 
}

exports.RoleDialog = RoleDialog;
