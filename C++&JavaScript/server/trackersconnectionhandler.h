#ifndef TRACKERSERVER_H
#define TRACKERSERVER_H

#include <trackerstatebase.h>
#include <trackerconnectionhandlerbase.h>
#include "AbstractParser.h"
#include "Factory.h"
#include "ilib/date.h"
#include <servicestorageconnectionhandler.h>

class TrackersConnectionHandler : public TrackerConnectionHandlerBase
{
public:
    TrackersConnectionHandler ( event_base* eventBase, OutBuffer &outBuffer, TrackerStateBase *state,const std::string &parser ) :
        TrackerConnectionHandlerBase ( eventBase, outBuffer, state->getConsumer(),state->getServiceStorage()),
        m_trackParser(getFactory().getParser(parser)),
        m_parserName(parser)
    {}

    virtual void onRead ( const char *inBuffer, size_t inBufferSize );
    virtual void onReadServiceStorage(storage::messageStorage message);
    ~TrackersConnectionHandler(){

        if (m_trackParser.get()){
            m_message.Clear();
            const std::string hash = m_trackParser->getHash();
            if (hash.size()){
                trackers::TrackerStatus trackerStatus;
                trackerStatus.set_disconnected_flag(true);
                trackerStatus.set_received_timestamp(ilib::Date::now().toUnixTime() - ilib::Date::now().getTZ() * 3600);
                m_message.add_tracker_status()->CopyFrom(trackerStatus);
                m_message.set_deviceid(hash);
                m_consumer->putMessage(m_message);
                LogConfig::logger()->debug2("~TrackersConnectionHandler, tracker status DISCONNECT send ");
            } else {
                LogConfig::logger()->error("NULL hash in ~TrackersConnectionHandler, tracker status DISCONNECT not send! ");
            }
        }
        ServiceStorageConnectionHandler<TrackersConnectionHandler, TrackerStateBase> * pointer =
                (ServiceStorageConnectionHandler<TrackersConnectionHandler, TrackerStateBase> *)m_serviceStorage->getPointerServiceStoroge();
        if (pointer)
            pointer->removeTracker(this);
    }
private:
    std::auto_ptr<AbstractParser> m_trackParser;
    const std::string m_parserName;
};

#endif // TRACKERSERVER_H
