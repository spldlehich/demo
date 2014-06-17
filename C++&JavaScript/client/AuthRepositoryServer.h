/*
 * AuthRepositoryServer.h
 *
 *  Created on: 18.03.2013
 *      Author: somebody
 */

#ifndef AUTHREPOSITORYSERVER_H_
#define AUTHREPOSITORYSERVER_H_

#include "services.h"
#include "authrepo/jsondbstructure.h"
#include "authrepo/commitnode.h"

class AuthRepositoryServer : public AuthRepositoryServiceServerIf 
{
public:
	AuthRepositoryServer(const std::string& dbStructureFile);
	virtual ~AuthRepositoryServer();
	
    virtual AuthRepositoryPatch sync(const AuthRepositoryPatch &clientSync);
	virtual void setUserPassword (const std::string &userStaticId, const std::string &newPassword);
	virtual AuthRepositoryPatch apply ( const AuthRepositoryPatch& clientSync );

	virtual std::string registerNewTrial ( const RegistrationParams& params );
	virtual AddUserResult addUser(const std::string &licenseStaticId, const std::string &login, const std::string &password, const std::string &fullName, const std::string &email, const std::string &phone, bool enabled);
	virtual AddUserResult updateUser ( const std::string& licenseStaticId, const std::string& login, const std::string& password, const std::string& fullName, const std::string& email, const std::string& phone, bool enabled, const std::string& userStaticId);
	virtual void setDeviceIdent(const std::string &deviceStaticId, const std::string &deviceident);

	virtual AddUserResult addUserWithRole(
		const std::string &licenseStaticId, const std::string &login, const std::string &password,
		const std::string &fullName, const std::string &email, const std::string &phone,
		bool enabled, const std::string& roleStaticId, const std::vector<std::string>& groupStaticIdList 
	);
	virtual AddUserResult updateUserWithRole( 
		const std::string& licenseStaticId, const std::string& login, const std::string& password,
		const std::string& fullName, const std::string& email, const std::string& phone, 
		bool enabled, const std::string& roleStaticId, const std::vector<std::string>& groupStaticIdList, 
		const std::string& userStaticId 
	);

private:
	struct PairKindMask{
		std::string kind;
		std::string mask;
		PairKindMask (const std::string _kind, const std::string _mask)
			:kind(_kind),mask(_mask){}
	};
	struct RolesParams {
		std::string name;
		bool admin;
		std::vector<PairKindMask> pairs;
	};

	void setCredentials(const std::string &userStaticId, const std::string &newPassword );
	RolesParams createRole(int administrativeMask, int userDefinedMask);
	authrepo::JSONDBStructure m_dbStructure;

	void validateTrialParams( authrepo::CommitNode& myHead, const RegistrationParams& params );
	void addTrial(authrepo::CommitNode& myHead, const RegistrationParams& params,  std::string &userStaticId,  std::string &licenseStaticId );

	void validateAddUserParams( authrepo::CommitNode& myHead, const std::string &licenseStaticId, const std::string &login, const std::string &password, const std::string &fullName, const std::string &email, const std::string &phone, bool enabled);
	std::string addUser(authrepo::CommitNode& myHead, 
		const std::string &licenseStaticId, const std::string &login, const std::string &password, 
		const std::string &fullName, const std::string &email, const std::string &phone, bool enabled, 
		const std::string& roleStaticId, const std::vector<std::string>& groupStaticIdList
	);

	void validateUpdateUserParams( authrepo::CommitNode& myHead, const std::string &licenseStaticId, const std::string &login, const std::string &password, const std::string &fullName, const std::string &email, const std::string &phone, bool enabled, const std::string &userStaticId);
	void updateUser(authrepo::CommitNode& myHead, 
		const std::string &licenseStaticId, const std::string &login, const std::string &password, 
		const std::string &fullName, const std::string &email, const std::string &phone,
		bool enabled, const std::string& roleStaticId, const std::vector<std::string>& groupStaticIdList, 
		const std::string &userStaticId
	);
	
	void ensureRootPermission();
};

#endif /* AUTHREPOSITORYSERVER_H_ */
