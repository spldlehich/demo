/*
 * AuthRepositoryServer.cpp
 *
 *  Created on: 18.03.2013
 *      Author: somebody
 */

#include "AuthRepositoryServer.h"
#include "authtools.h"
#include "authrepo/jsonpatch.h"
#include "authrepo/dbdatasource.h"
#include "authrepo/dbdatacollector.h"
#include "authrepo/patcher.h"
#include "authrepo/diffcalculator.h"
#include "authrepo/util.h"
#include "activedeviceslist.h"
#include "json.h"
#include "authrepo/util.h"

AuthRepositoryServer::AuthRepositoryServer (
	const std::string& dbStructureFile )
	: m_dbStructure ( dbStructureFile )
{
	this->ensureRootPermission();
}

void AuthRepositoryServer::ensureRootPermission()
{
	LogConfig::logger()->info("Begin check root role");

	authrepo::DBDataSource src;
	authrepo::CommitNode myHead ( &m_dbStructure, src.getTaggedCommit( "head" ) );
	//NOTE: load commit tree
	myHead.load ( &src );
	//NOTE: Allow any changes on commit tree
	myHead.setRootPermissions();

	//NOTE: scheme has user entity?
	const authrepo::DBEntity * p_userEntity = 0;
	try {
		p_userEntity = m_dbStructure.getByName( "user" );
	} catch(authrepo::util::runtime_error & err) {
		LogConfig::logger()->error("Seems scheme has no information about user entity: %s", err.what());
		return;
	}

	//NOTE: should not be the case since exception catched above
	if(!p_userEntity) {
		LogConfig::logger()->error("Seems scheme has no information about user entity");
		return;
	}

	//NOTE: commit contains root user?
	authrepo::DataNode * p_user = myHead.getIndex()->getIndex(p_userEntity)->findStaticId("root_user");
	if(!p_user) {
		LogConfig::logger()->error("Seems root user not found");
		return;
	}

	//NOTE: root user has a license?
	authrepo::ObjectNode * p_license = p_user->getParent();
	if(!p_license) {
		LogConfig::logger()->error("Seems root user license found");
		return;
	}

	//NOTE: license is connected to group?
	authrepo::ObjectNode * p_userRootGroup = p_license->getParent();
	if(!p_userRootGroup) {
		LogConfig::logger()->error("Seems root's root group not found");
		return;
	}

	//NOTE: no need to save changes sofar
	bool hasChanges = false;

	bool hasRole = false;
	
	//NOTE: read the security role id form user's fields
	const std::map<std::string, std::string>::const_iterator cit_srid = p_user->fields().find("srid");
	//NOTE: when srid field is set, check role is there too
	if(cit_srid  != p_user->fields().end()) {
		//NOTE: scheme has role entity?
		const authrepo::DBEntity * p_roleEntity = 0;
		try {
			p_roleEntity = m_dbStructure.getByName( "role" );
		} catch(authrepo::util::runtime_error & err) {
			LogConfig::logger()->error("Seems scheme has no information about user entity: %s", err.what());
			return;
		}
		//NOTE: check user's role exists
		authrepo::DataNode * p_role = myHead.getIndex()->getIndex(p_roleEntity)->findStaticId(cit_srid->second);
		if(p_role) {
			//NOTE: role found, cancel role creation
			hasRole = true;
		}
	}
	
	if(!hasRole) {
		//NOTE: the security role id field is blank - no roles found
		LogConfig::logger()->info("No role found on root user, create one");
		authrepo::DataNode *role = p_userRootGroup->createChild ( "role", authrepo::util::generateId ( "" ) );
		role->setField("name", "Built-in Administrator");
		p_user->setField("srid",role->getStaticId());
		const std::map<std::string, authrepo::DBEntity *> & entities = m_dbStructure.getAll();
		for ( 
			std::map<std::string, authrepo::DBEntity *>::const_iterator c_itKind = entities.begin(); 
			c_itKind!= entities.end(); ++c_itKind 
		) {
			authrepo::DataNode * permissionrole = role->createChild ( "permissionrole", authrepo::util::generateId ( "" ) );
			permissionrole->setField ( "kind", c_itKind->first ); //NOTE: Table name
			permissionrole->setField ( "mask", "15" ); //FIXME: Full permissions hardcoded
		}
		//NOTE: yep, save the role
		hasChanges = true;
	}

	//NOTE: scheme has grouplink entity?
	const authrepo::DBEntity * p_grouplinkEntity = 0;
	try {
		p_grouplinkEntity = m_dbStructure.getByName( "grouplink" );
	} catch(authrepo::util::runtime_error & err) {
		LogConfig::logger()->error("Seems scheme has no information about grouplink entity: %s", err.what());
		return;
	}

	authrepo::TableIndex *p_grouplinkIndex = myHead.getIndex()->getIndex(p_grouplinkEntity);
	bool hasGrouplink = false;
	//NOTE: check the grouplink is there
	if(p_grouplinkIndex) {
		for( std::map< std::string, authrepo::DataNode*>::const_iterator iGrouplink = p_grouplinkIndex->data().begin();
			iGrouplink != p_grouplinkIndex->data().end();
			++iGrouplink
		) {
			std::map<std::string, std::string>::const_iterator citGroup = iGrouplink->second->fields().find("sgid");
			if(citGroup != iGrouplink->second->fields().end() && citGroup->second == p_userRootGroup->getStaticId()) {
				//NOTE: root's grop grouplink found, no need to make any changes
				hasGrouplink = true;
				break;
			}
		}
	}

	if(!hasGrouplink) {
		//NOTE: link root group with root user
		LogConfig::logger()->info("Restore grouplink to root group");
		authrepo::DataNode *grouplink = p_user->createChild ( "grouplink", authrepo::util::generateId ( "" ) );
		grouplink->setField("sgid", p_userRootGroup->getStaticId());
		hasChanges = true;
	}

	if(hasChanges) {
		LogConfig::logger()->info("Save role");
		DBSession s = DBSessionFactory::getSession();
		s.BeginTransaction();
		authrepo::DBDataCollector coll ( s );
		myHead.storeUpdates ( &coll );
		coll.writeTag ( "head", myHead.getId() );
		s.CommitTransaction();
	}

	LogConfig::logger()->info("Root role check finished successfully");
}

AuthRepositoryServer::~AuthRepositoryServer()
{
}

AuthRepositoryPatch AuthRepositoryServer::sync (
	const AuthRepositoryPatch& clientSync )
{
//	throw std::runtime_error("test error");

	//LogConfig::logger()->debug("repo input: %s %s", clientSync.oldCommit.c_str(), clientSync.diffJson.c_str());

	authrepo::DBDataSource src;


	if ( clientSync.get_diffJson() == "{}" && clientSync.get_oldCommit() == src.getTaggedCommit ( "head" ) ) {
		//no changes on both sides
		AuthRepositoryPatch result;
		result.set_diffJson("{}");
		result.set_newCommit(clientSync.get_oldCommit());
		result.set_oldCommit(clientSync.get_oldCommit());

		return result;
	}

	authrepo::PPatch patch = authrepo::JsonPatch::parseString ( clientSync.get_diffJson() );

	//load current head
	authrepo::PCommitNode currentHead ( new authrepo::CommitNode ( &m_dbStructure, src.getTaggedCommit ( "head" ) ) );
	currentHead->load ( &src );

	try {
		currentHead->setUserPermissions ( m_userStaticId );

		//apply user patch
		authrepo::GeneralPatcher p ( currentHead.getAddr(), patch );
		p.apply();

		//all ok store results
		DBSession s = DBSessionFactory::getSession();

		s.BeginTransaction();
		authrepo::DBDataCollector coll ( s );
		currentHead->storeUpdates ( &coll );
		coll.writeTag ( "head", currentHead->getId() );

		ActiveDevicesList::update ( s );

		s.CommitTransaction();
	} catch ( authrepo::util::runtime_error &ue ) {
		LogConfig::logger()->error ( "client patch problem: %s", ue.what() );

		//return full patch
		authrepo::PCommitNode cleanHead ( new authrepo::CommitNode ( &m_dbStructure, src.getTaggedCommit ( "head" ) ) );
		cleanHead->load ( &src );

		currentHead = cleanHead;
	}

	//fast forward case
// 	if (fastForwardFlag)
// 	{
// 		AuthRepositoryPatch result;
// 		result.diffJson = "{}";
// 		result.newCommit = currentHead->getId();
// 		result.oldCommit = clientSync.oldCommit;
//
// 		LogConfig::logger()->info("repo fast forward: %s %s", result.diffJson.c_str(), result.newCommit.c_str());
// 		return result;
// 	}

	//default
	authrepo::PCommitNode clientSyncedCommit ( new authrepo::CommitNode ( &m_dbStructure, "initial" ) );

	//try load client
	try {
		authrepo::PCommitNode realClientSynced ( new authrepo::CommitNode ( &m_dbStructure, clientSync.get_oldCommit() ) );
		realClientSynced->load ( &src );
		clientSyncedCommit = realClientSynced;
	} catch ( authrepo::util::runtime_error &ue ) {
		LogConfig::logger()->error ( "client commit loading problem:\r\n %s\r\n %s", clientSync.get_oldCommit().c_str(), ue.what() );
	}

	authrepo::DiffCalculator dc ( clientSyncedCommit, currentHead );

	AuthRepositoryPatch result;
	result.set_diffJson(authrepo::JsonPatch::stringify ( dc.diff ( m_userStaticId ) ));
	result.set_newCommit(currentHead->getId());
	result.set_oldCommit(clientSyncedCommit->getId());

	//LogConfig::logger()->debug("repo output: %s", result.newCommit.c_str());

	return result;
}

void AuthRepositoryServer::setUserPassword ( const std::string &userStaticId, const std::string &newPassword )
{
	LogConfig::logger()->info ( "changing password for: %s", userStaticId.c_str() );
	//check permissions
	if ( m_userStaticId == userStaticId ) {
		setCredentials ( userStaticId, newPassword );
	} else {
		authrepo::DBDataSource src;
		authrepo::CommitNode myHead ( &m_dbStructure, src.getTaggedCommit ( "head" ) );
		myHead.load ( &src );
		myHead.setUserPermissions ( m_userStaticId );

		const authrepo::DBEntity *user = m_dbStructure.getByName ( "user" );
		authrepo::DataNode * userNode = myHead.getIndex()->getIndex ( user )->findStaticId ( userStaticId );

		if ( userNode ) {
			authrepo::PermissionInfo p ( authrepo::pfEditPermissions, user );
			userNode->checkPermission ( p );

			if ( p.isNoFlags() )
				setCredentials ( userStaticId, newPassword );
			else
				throw std::runtime_error ( "no permissions for user: " + userStaticId );
		} else
			throw std::runtime_error ( "unknown user: " + userStaticId );
	}
}

void AuthRepositoryServer::setCredentials ( const std::string &userStaticId, const std::string &newPassword )
{
	std::string newSalt = authtools::generateRandomSalt();
	std::string pwdHash = authtools::calculatePwdHash ( newPassword, newSalt );

	DBSession s = DBSessionFactory::getSession();
	s.BeginTransaction();
	s.execUpdate ( "DELETE FROM repo.user_credentials WHERE userstaticid = %s", userStaticId.c_str() );
	s.execUpdate ( "INSERT INTO repo.user_credentials(userstaticid, pwdhash, pwdsalt) VALUES (%s, %s, %s)", userStaticId.c_str(), pwdHash.c_str(), newSalt.c_str() );
	s.CommitTransaction();
}

AuthRepositoryPatch AuthRepositoryServer::apply ( const AuthRepositoryPatch& clientSync )
{
	LogConfig::logger()->info ( "input: %s %s", clientSync.get_oldCommit().c_str(), clientSync.get_diffJson().c_str() );
	authrepo::PPatch patch = authrepo::JsonPatch::parseString ( clientSync.get_diffJson() );

	authrepo::DBDataSource src;

	//do patch

	authrepo::CommitNode myHead ( &m_dbStructure, src.getTaggedCommit ( "head" ) );
	myHead.load ( &src );
	myHead.setUserPermissions ( m_userStaticId );
	authrepo::GeneralPatcher p ( &myHead, patch );
	p.apply();
	DBSession s = DBSessionFactory::getSession();
	s.BeginTransaction();
	authrepo::DBDataCollector coll ( s );
	myHead.storeUpdates ( &coll );
	coll.writeTag ( "head", myHead.getId() );
	s.CommitTransaction();


	//return current db status
	authrepo::CommitNode initialCommit ( &m_dbStructure, "initial" );
	initialCommit.load ( &src );

	AuthRepositoryPatch result;
	authrepo::DiffCalculator dc ( &initialCommit, &myHead );
	result.set_diffJson(authrepo::JsonPatch::stringify ( dc.diff ( m_userStaticId ) ));
	result.set_newCommit(myHead.getId());

	LogConfig::logger()->debug ( "repo output: %s %s", result.get_diffJson().c_str(), result.get_newCommit().c_str() );

	return result;
}

namespace
{
struct ValidationException : public std::runtime_error {
	explicit ValidationException ( const std::string &message ) : std::runtime_error ( message ) {}
};
}

std::string AuthRepositoryServer::registerNewTrial ( const RegistrationParams& params )
{
	try {
		authrepo::DBDataSource src;
		authrepo::CommitNode myHead ( &m_dbStructure, src.getTaggedCommit ( "head" ) );
		myHead.load ( &src );
		myHead.setRootPermissions();

		validateTrialParams ( myHead, params );

		std::string userStaticId;
		std::string licenseStaticId;
		addTrial ( myHead, params, userStaticId, licenseStaticId );

		//write
		DBSession s = DBSessionFactory::getSession();
		s.BeginTransaction();
		authrepo::DBDataCollector coll ( s );
		myHead.storeUpdates ( &coll );
		coll.writeTag ( "head", myHead.getId() );


		std::string newSalt = authtools::generateRandomSalt();
		std::string pwdHash = authtools::calculatePwdHash ( params.get_password(), newSalt );
		std::string computeridToken = authtools::calculatePwdHash ( licenseStaticId, authtools::generateRandomSalt());

		s.execUpdate ( "DELETE FROM repo.user_credentials WHERE userstaticid = %s", userStaticId.c_str() );
		s.execUpdate ( "INSERT INTO repo.user_credentials(userstaticid, pwdhash, pwdsalt) VALUES (%s, %s, %s)", userStaticId.c_str(), pwdHash.c_str(), newSalt.c_str() );
		s.execUpdate ( "INSERT INTO license_token(licensestaticid, computerid_token) VALUES (%s, %s)", licenseStaticId.c_str(), computeridToken.c_str());

		s.CommitTransaction();
	} catch ( ValidationException &re ) {
		return std::string ( re.what() );
	}

	return std::string();
}

void AuthRepositoryServer::validateTrialParams ( authrepo::CommitNode& myHead, const RegistrationParams& params )
{
	//validate params
	if ( myHead.getLoginIndex()->find ( params.get_login() ) )
		throw ValidationException ( "user exists" );

	if ( params.get_login().length() < 6 )
		throw ValidationException ( "login is too short, at least 6 letters required" );

	if ( params.get_companyName().empty() )
		throw ValidationException ( "Non-empty company name required" );

	if ( params.get_password().length() < 5 )
		throw ValidationException ( "password is too short, at least 5 letters required" );
}

AuthRepositoryServer::RolesParams AuthRepositoryServer::createRole(int _adminMask, int _userMask) {
	RolesParams r;
	//FIXME: integer to char arra, Has to be written once where PairKindMask turns into a patch.
	char adminMask[50];
	::sprintf(adminMask, "%i", _adminMask);
	char userMask[50];
	::sprintf(userMask, "%i", _userMask);
	const std::map<std::string, authrepo::DBEntity *> & entities = m_dbStructure.getAll();
	for ( std::map<std::string, authrepo::DBEntity *>::const_iterator it = entities.begin(); it!= entities.end(); ++it ) {
		if (
			it->first == "license" ||
			it->first == "user" ||
			it->first == "role" ||
			it->first == "permissionrole" ||
			it->first == "grouplink"
		) {
			if(_adminMask) { //NOTE: To avoid zero-mask assignement
				r.pairs.push_back(PairKindMask(it->first,adminMask)); 
			}
		} else if (_userMask) {
			r.pairs.push_back(PairKindMask(it->first,userMask));
		}
	}
	return r;
}

void AuthRepositoryServer::addTrial ( authrepo::CommitNode& myHead, const RegistrationParams& params,  std::string &userStaticId, std::string &licenseStaticId )
{
	//go
	authrepo::DataNode *root_user = myHead.getLoginIndex()->find ( "root" );
	if ( !root_user ) throw std::runtime_error ( "!root_user" );

	authrepo::DataNode *root_license = dynamic_cast<authrepo::DataNode *> ( root_user->getParent() );
	if ( !root_license ) throw std::runtime_error ( "!root_license" );

	std::string root_group_staticid = root_license->fields().at ( "rootgroup" );

	authrepo::DataNode *root_group = myHead.getIndex()->getIndex ( m_dbStructure.getByName ( "group" ) )->findStaticId ( root_group_staticid );
	if ( !root_group ) throw std::runtime_error ( "!root_group" );


	authrepo::DataNode *user_root_group = root_group->createChild ( "group", authrepo::util::generateId ( "" ) );
	{
		user_root_group->setField ( "title", params.get_companyName() );
	}

	authrepo::DataNode *user_license = user_root_group->createChild ( "license", authrepo::util::generateId ( "" ) );
	{
		user_license->setField ( "rootgroup", user_root_group->getStaticId() );
		user_license->setField ( "title", params.get_companyName() );
	}

	authrepo::DataNode *user = user_license->createChild ( "user", authrepo::util::generateId ( "" ) );
	{
		user->setField ( "login", params.get_login() );
		user->setField ( "welcome_name", params.get_welcomeName() );
		user->setField ( "email", params.get_email() );
		user->setField ( "enabled", "true" );
	}

	//create permissions
	authrepo::DataNode *grouplink = user->createChild ( "grouplink", authrepo::util::generateId ( "" ) );
	grouplink->setField("sgid", user_root_group->getStaticId());

	std::vector<RolesParams> rolesParams;

	// Administrator: he can do whatever he wants in scope of his compnay's root group
	RolesParams adminRole = this->createRole(15, 15);
	adminRole.name = "Administrator";
	adminRole.admin = true;
	rolesParams.push_back(adminRole);

	// Operator, he can only view devices, places, geofnces
	RolesParams operatorRole = this->createRole(0, 1);
	operatorRole.name = "Operator";
	operatorRole.admin = false;
	rolesParams.push_back(operatorRole);

	// Manager, he can see users, his company and roles; create devices, places, geofences
	RolesParams managerRole = this->createRole(1, 15);
	managerRole.name = "Manager";
	managerRole.admin = false;
	rolesParams.push_back(managerRole);

	// create structure permissions

	for(size_t i =0; i < rolesParams.size(); ++i){
		authrepo::DataNode *role = user_root_group->createChild ( "role", authrepo::util::generateId ( "" ) );
		role->setField("name", rolesParams[i].name);
		if (rolesParams[i].admin)
			user->setField("srid",role->getStaticId()); // For user -> role
		for (size_t j = 0; j < rolesParams[i].pairs.size(); ++j){
			authrepo::DataNode *permissionrole = role->createChild ( "permissionrole", authrepo::util::generateId ( "" ) );
			permissionrole->setField ( "kind", rolesParams[i].pairs[j].kind );
			permissionrole->setField ( "mask", rolesParams[i].pairs[j].mask );
		}
	}

	userStaticId = user->getStaticId();
	licenseStaticId = user_license->getStaticId();
}

AddUserResult AuthRepositoryServer::addUser ( 
	const std::string &licenseStaticId, const std::string &login, const std::string &password, 
	const std::string &fullName, const std::string &email, const std::string &phone,
	bool enabled 
)
{
	std::vector<std::string> dummy;
	return this->addUserWithRole ( 
		licenseStaticId, login, password, 
		fullName, email, phone, 
		enabled, "", dummy 
	);
}

AddUserResult AuthRepositoryServer::addUserWithRole ( 
	const std::string &licenseStaticId, const std::string &login, const std::string &password, 
	const std::string &fullName, const std::string &email, const std::string &phone, 
	bool enabled, const std::string& roleStaticId, const std::vector<std::string>& groupStaticIdList)
{
	AddUserResult result;

	try {
		authrepo::DBDataSource src;
		authrepo::CommitNode myHead ( &m_dbStructure, src.getTaggedCommit ( "head" ) );
		myHead.load ( &src );
		myHead.setUserPermissions ( m_userStaticId );

		validateAddUserParams ( myHead, licenseStaticId, login, password, fullName, email, phone, enabled );
		std::string userStaticId = addUser ( myHead, 
			licenseStaticId, login, password, 
			fullName, email, phone, enabled,
			roleStaticId, groupStaticIdList
		);

		//NOTE: write
		DBSession s = DBSessionFactory::getSession();
		s.BeginTransaction();
		authrepo::DBDataCollector coll ( s );
		myHead.storeUpdates ( &coll );
		coll.writeTag ( "head", myHead.getId() );

		std::string newSalt = authtools::generateRandomSalt();
		std::string pwdHash = authtools::calculatePwdHash ( password, newSalt );
		std::string computeridToken = authtools::calculatePwdHash ( licenseStaticId, authtools::generateRandomSalt() );

		s.execUpdate ( "DELETE FROM repo.user_credentials WHERE userstaticid = %s", userStaticId.c_str() );
		s.execUpdate ( "INSERT INTO repo.user_credentials(userstaticid, pwdhash, pwdsalt) VALUES (%s, %s, %s)", userStaticId.c_str(), pwdHash.c_str(), newSalt.c_str() );

		s.CommitTransaction();

		result.set_successful ( true );
	} catch ( ValidationException &re ) {
		result.set_successful ( false );
		result.set_errorMessage ( re.what() );
	} catch ( authrepo::util::permissions_check_error& e ) {
		result.set_successful ( false );
		result.set_errorMessage ( "insufficient permissions" );
	}

	return result;
}

void AuthRepositoryServer::validateAddUserParams ( authrepo::CommitNode& myHead, const std::string& licenseStaticId, const std::string& login, const std::string &password, const std::string& fullName, const std::string& email, const std::string& phone, bool enabled )
{
	//validate params
	if ( !myHead.getIndex()->getIndex ( m_dbStructure.getByName ( "license" ) )->findStaticId ( licenseStaticId ) )
		throw ValidationException ( "unknown license" );

	if ( myHead.getLoginIndex()->find ( login ) )
		throw ValidationException ( "user exists" );

	if ( login.length() < 6 )
		throw ValidationException ( "login is too short, at least 6 letters required" );

	if ( password.length() < 5 )
		throw ValidationException ( "password is too short, at least 5 letters required" );
}

std::string AuthRepositoryServer::addUser ( 
	authrepo::CommitNode& myHead, 
	const std::string& licenseStaticId, const std::string& login, const std::string &password, 
	const std::string& fullName, const std::string& email, const std::string& phone, 
	bool enabled, const std::string& roleStaticId, const std::vector<std::string>& groupStaticIdList
)
{
	authrepo::DataNode *user_license = myHead.getIndex()->getIndex ( m_dbStructure.getByName ( "license" ) )->findStaticId ( licenseStaticId );
	if ( !user_license ) throw std::runtime_error ( "!user_license" );

	authrepo::DataNode *user_root_group = myHead.getIndex()->getIndex ( m_dbStructure.getByName ( "group" ) )->findStaticId ( user_license->fields().at("rootgroup") );
    if ( !user_root_group ) throw std::runtime_error ( "!user_root_group" );

	authrepo::DataNode *user = user_license->createChild ( "user", authrepo::util::generateId ( "" ) );
	{
		user->setField ( "login", login );
		user->setField ( "welcome_name", fullName );
		user->setField ( "email", email );
		user->setField ( "phone", phone );
		user->setField ( "enabled", enabled ? "true" : "false" );
		if(!roleStaticId.empty()) {
			user->setField ( "srid", roleStaticId );
		}
	}

	//NOTE: link groups with newly created user
	for(std::vector<std::string>::const_iterator it = groupStaticIdList.begin(); it != groupStaticIdList.end(); ++it) 
	{
		authrepo::DataNode *grouplink = user->createChild ( "grouplink", authrepo::util::generateId ( "" ) );
		grouplink->setField("sgid", *it);
		myHead.checkSharePermission(grouplink);
	}

	return user->getStaticId();
}

AddUserResult AuthRepositoryServer::updateUser ( 
	const std::string &licenseStaticId, const std::string &login, const std::string &password, 
	const std::string &fullName, const std::string &email, const std::string &phone, 
	bool enabled, const std::string &userStaticId
)
{
	std::vector<std::string> dummy;
	return this->updateUserWithRole( 
		licenseStaticId, login, password, 
		fullName, email, phone, 
		enabled, "", dummy, userStaticId
	);
}

AddUserResult AuthRepositoryServer::updateUserWithRole ( 
	const std::string &licenseStaticId, const std::string &login, const std::string &password, 
	const std::string &fullName, const std::string &email, const std::string &phone, 
	bool enabled, const std::string& roleStaticId, const std::vector<std::string> & groupStaticIdList, 
	const std::string &userStaticId
)
{
	AddUserResult result;

	try {
		authrepo::DBDataSource src;
		authrepo::CommitNode myHead ( &m_dbStructure, src.getTaggedCommit ( "head" ) );
		myHead.load ( &src );
		myHead.setUserPermissions ( m_userStaticId );

		validateUpdateUserParams ( myHead, licenseStaticId, login, password, fullName, email, phone, enabled, userStaticId );
		updateUser ( myHead, 
			licenseStaticId, login, password, 
			fullName, email, phone, enabled, 
			roleStaticId, groupStaticIdList, userStaticId
		);

		//write
		DBSession s = DBSessionFactory::getSession();
		s.BeginTransaction();
		authrepo::DBDataCollector coll ( s );
		myHead.storeUpdates ( &coll );
		coll.writeTag ( "head", myHead.getId() );

		if (!password.empty()) {
			std::string newSalt = authtools::generateRandomSalt();
			std::string pwdHash = authtools::calculatePwdHash ( password, newSalt );
			std::string computeridToken = authtools::calculatePwdHash ( licenseStaticId, authtools::generateRandomSalt() );

			s.execUpdate ( "DELETE FROM repo.user_credentials WHERE userstaticid = %s", userStaticId.c_str() );
			s.execUpdate ( "INSERT INTO repo.user_credentials(userstaticid, pwdhash, pwdsalt) VALUES (%s, %s, %s)", userStaticId.c_str(), pwdHash.c_str(), newSalt.c_str() );
		}

		s.CommitTransaction();

		result.set_successful ( true );
	} catch ( ValidationException &re ) {
		result.set_successful ( false );
		result.set_errorMessage ( re.what() );
	} catch ( authrepo::util::permissions_check_error& e ) {
		result.set_successful ( false );
		result.set_errorMessage ( "insufficient permissions" );
	}

	return result;
}

void AuthRepositoryServer::validateUpdateUserParams ( authrepo::CommitNode& myHead, const std::string& licenseStaticId, const std::string& login, const std::string &password, const std::string& fullName, const std::string& email, const std::string& phone, bool enabled, const std::string &userStaticId)
{
	if ( !password.empty() && password.length() < 5 )
		throw ValidationException ( "password is too short, at least 5 letters required" );
}

void AuthRepositoryServer::updateUser ( 
	authrepo::CommitNode& myHead, 
	const std::string& licenseStaticId, const std::string& login, const std::string &password, 
	const std::string& fullName, const std::string& email, const std::string& phone, 
	bool enabled, const std::string& roleStaticId, const std::vector<std::string>& groupStaticIdList,
	const std::string &userStaticId
)
{
	authrepo::DataNode *user_license = myHead.getIndex()->getIndex ( m_dbStructure.getByName ( "license" ) )->findStaticId ( licenseStaticId );
	if ( !user_license ) throw ValidationException ( "unknown license" );

	authrepo::DataNode *login_user = myHead.getLoginIndex()->find ( login );
	authrepo::DataNode *user = myHead.getIndex()->getIndex ( m_dbStructure.getByName ( "user" ) )->findStaticId ( userStaticId );
	if ( !user ) throw ValidationException ( "unknown user" );

	if (login_user && login_user != user)
		throw ValidationException ( "login exists" );

	authrepo::PDataNode temp = user->getParent()->detachChild(user);
	user_license->attachChild(temp);
	{
		user->setField ( "login", login );
		user->setField ( "welcome_name", fullName );
		user->setField ( "email", email );
		user->setField ( "phone", phone );
		user->setField ( "enabled", enabled ? "true" : "false" );
		if(!roleStaticId.empty()) {
			user->setField ( "srid", roleStaticId );
		}
	}

	//NOTE: merge accessible group list
	const authrepo::DBEntity *p_grouplinkEntity = m_dbStructure.getByName( "grouplink" );
	
	//NOTE: detach all grouplinks from user, collect detached in a map
	std::map<std::string, authrepo::PDataNode> currentGroupLink;
	for(
		authrepo::TChildrenMap::const_iterator itChildren = user->getChildren().begin(); 
		itChildren != user->getChildren().end();
		++itChildren
	) {
		//NOTE: loog only at grouplink items
		if(itChildren->second->getTypeEntity() == p_grouplinkEntity) {
			//NOTE: look for group staticid, skip if not found
			std::map<std::string, std::string>::const_iterator itField = itChildren->second->fields().find("sgid");
			if(itField != itChildren->second->fields().end()) {
				currentGroupLink[itField->second] = itChildren->second;
				user->detachChild(itChildren->second);
			}
		}
	}

	//NOTE: pass througs the actual groups
	for(
		std::vector<std::string>::const_iterator groupIt = groupStaticIdList.begin();
		groupIt != groupStaticIdList.end();
		++groupIt
	) {
		std::map<std::string, authrepo::PDataNode>::iterator it = currentGroupLink.find( *groupIt );
		if(it == currentGroupLink.end()) {
			//NOTE: create new grouplink
			authrepo::DataNode *grouplink = user->createChild ( "grouplink", authrepo::util::generateId ( "" ) );
			grouplink->setField("sgid", *groupIt);
			myHead.checkSharePermission(grouplink);
		} else {
			//NOTE: attach existing grouplink
			user->attachChild(it->second);
		}
	}

}

void AuthRepositoryServer::setDeviceIdent ( const std::string& deviceStaticId, const std::string& deviceident )
{
	authrepo::DBDataSource src;
	authrepo::CommitNode myHead ( &m_dbStructure, src.getTaggedCommit ( "head" ) );
	myHead.load ( &src );
	myHead.setUserPermissions ( m_userStaticId );

	authrepo::DataNode *device = myHead.getIndex()->getIndex ( m_dbStructure.getByName ( "device" ) )->findStaticId ( deviceStaticId );
	if ( !device ) throw std::runtime_error ( "AuthRepositoryServer::setDeviceIdent unknown device: " + deviceStaticId );

	device->setField("deviceident", deviceident);
	device->setField("friendsstatus", "2");

	//write
	DBSession s = DBSessionFactory::getSession();
	s.BeginTransaction();
	authrepo::DBDataCollector coll ( s );
	myHead.storeUpdates ( &coll );
	coll.writeTag ( "head", myHead.getId() );

	s.CommitTransaction();
}
