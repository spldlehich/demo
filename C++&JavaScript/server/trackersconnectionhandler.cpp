#include "trackersconnectionhandler.h"
#include "logconfig.h"
#include <ExeptionsParser.h>

void TrackersConnectionHandler::onRead(const char *inBuffer, size_t inBufferSize)
{
	std::string m_package;
	m_package.append(inBuffer, inBufferSize);
	LogConfig::logger()->debug2("%s: Dump packege hex: %s", m_parserName.c_str(), ValueReader::dumpPackage(m_package).c_str());
	if (m_trackParser.get() == NULL)
		throw std::runtime_error("Parser not found!");
	m_trackParser->writeToParser(m_package);
	m_package.erase();

	bool doParse = true;
	trackers::TrackerStatus trackerStatus;
	do {
		const size_t currentPosStart = m_trackParser->getPositionPackage();
		try {
			trackerStatus.Clear();
			trackerStatus.set_connected_flag(true);
			trackerStatus.set_received_timestamp(ilib::Date::now().toUnixTime() - ilib::Date::now().getTZ() * 3600);
			m_message.add_tracker_status()->CopyFrom(trackerStatus);
			LogConfig::logger()->debug2("Tracker status CONNECT send ");
			if (m_trackParser->needParse()){
				m_trackParser->parse(m_message);
				if (m_trackParser->getPositionPackage() == currentPosStart)
					throw ExceptionParserTrash();
			}
			else
				doParse = false;
		}
		catch (ExceptionParserLittleData &exc1) {
			LogConfig::logger()->info(" parser exception: %s", exc1.what());
			m_trackParser->setPositionPackage(currentPosStart);
			doParse = false;
		}

	} while (doParse);
	m_trackParser->finishParse();
	m_consumer->putMessage(m_message);
	m_message.Clear();
	storage::messageStorage myMessageStorage;
	if (inBufferSize > 0)
		if (m_trackParser->needRequestToServiceStorage(myMessageStorage)){
			m_serviceStorage->putMessage(myMessageStorage);
			ServiceStorageConnectionHandler<TrackersConnectionHandler, TrackerStateBase> * pointer =
					(ServiceStorageConnectionHandler<TrackersConnectionHandler, TrackerStateBase> *)m_serviceStorage->getPointerServiceStoroge();
			if (pointer)
				pointer->appendTracker(this);
		}
	if (m_trackParser->needReadFromParser()){
		const std::string bin = m_trackParser->readFromParser();
		LogConfig::logger()->debug2("send bytes(hex) = %s",
					ValueReader::dumpPackage(bin).c_str());
		m_outBuffer.write(bin.c_str(), bin.size());
	}
}

void TrackersConnectionHandler::onReadServiceStorage(storage::messageStorage message) {
	if (m_trackParser.get() == NULL)
		throw std::runtime_error("Parser not found!");
	m_trackParser->responseFromServiceStorage(message);
	onRead(NULL,0);
}
