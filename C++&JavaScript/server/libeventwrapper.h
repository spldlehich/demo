#ifndef LIBEVENTWRAPPER_H
#define LIBEVENTWRAPPER_H

#include <unistd.h>
#include <cstring>
#include <event2/dns.h>
#include <event2/event.h>
#include <event2/listener.h>
#include <event2/bufferevent.h>
#include <event2/buffer.h>
#include <event2/util.h>
#include <iostream>
#include <stdexcept>
#include <vector>
#include <auto_ptr.h>
#include <arpa/inet.h>
#include "logconfig.h"
#include "storage.pb.h"

/*
 * libevent out buffer wrapper
 */

class OutBuffer
{
    bufferevent* m_bev;
public:
    OutBuffer() : m_bev ( 0 ) {}

    void setEvent ( bufferevent* bev )
    {
        m_bev =  bev;
    }

    void write ( const void *data, size_t length )
    {
        if ( !m_bev )
            throw std::runtime_error ( "writing to invalid buffer" );

        if ( 0 > bufferevent_write ( m_bev, data, length ) )
            throw std::runtime_error ( "error writing to buffer" );
    };
};

/*
 * connection handler stub
 * all custom connection handlers should drive from ConnectionHandlerBase
 *
 * outBuffer - write buffer for connection
 */
class ConnectionHandlerBase
{
public:
    ConnectionHandlerBase ( event_base* eventBase, OutBuffer &outBuffer ) : m_eventBase ( eventBase ), m_outBuffer ( outBuffer )
    {
    }

    virtual ~ConnectionHandlerBase() {}

    /*
     * some data received
     * write to m_outBuffer
     */
    virtual void onRead ( const char *inBuffer, size_t inBufferSize ) {};

    /*
     * after connection established
     */
    virtual void onConnected () {};

    /*
     * 1/10 sec resolution
     */
    virtual void onTimer () {}

    /*
     * on connection error (before destructor)
     */
    virtual void onError() {}

    /*
     * some data received
     * write to m_outBuffer in storage
     */
    virtual void onReadServiceStorage( storage::messageStorage message) {}
protected:
    event_base* m_eventBase;
    OutBuffer &m_outBuffer;
};


/*
 * libevent connection internal wrapper
 *
 * TConnectionHandler - have same methods as ConnectionHandlerBase and constructor (event_base* eventBase, OutBuffer &outBuffer, TConnectionHandlerParameter *state)
 * TConnectionHandlerParameter - parameter value for TConnectionHandler constructor
 *
 */

template<class TConnectionHandler, class TConnectionHandlerParameter>
class InternalConnectionHandler
{
    event_base* m_eventBase;
    bufferevent* m_bev;
    event *m_timer;

    std::string m_connectionId;
    TConnectionHandler m_connectionHandler;
    typedef InternalConnectionHandler<TConnectionHandler, TConnectionHandlerParameter> TThisType;

    std::vector<char> m_inBuffer;
    OutBuffer m_outBuffer;
public:
    bufferevent* getBufferEvent()
    {
        return m_bev;
    }

    InternalConnectionHandler ( event_base* eventBase, evutil_socket_t sock, const char *connectionId, TConnectionHandlerParameter *connectionHandlerParameter, const std::string &parser  ) :
        m_eventBase ( eventBase ),
        m_bev ( 0 ),
        m_timer ( 0 ),
        m_connectionId ( connectionId ),
        m_connectionHandler ( eventBase, m_outBuffer, connectionHandlerParameter,parser ),
        m_inBuffer ( 32*1024 )
    {

        m_bev = bufferevent_socket_new ( eventBase, sock, BEV_OPT_CLOSE_ON_FREE );

        if ( !m_bev )
            throw std::runtime_error ( "error creating buffer event" );

        m_outBuffer.setEvent ( m_bev );

        bufferevent_setcb ( m_bev, TThisType::readCallback, TThisType::readCallback, TThisType::eventCallback, this );

        int res = bufferevent_enable ( m_bev, EV_READ | EV_WRITE );
        if ( 0 != res )
            throw std::runtime_error ( "error bufferevent_enable" );
        if ( sock > 0 )
            internalConnected();
    };

    void internalConnected()
    {
        if ( !m_timer )
        {
            m_timer = event_new ( m_eventBase, -1, EV_PERSIST, TThisType::timerCallback, this );

            if ( !m_timer )
                throw std::runtime_error ( "error creating timer event" );

            timeval timerStep;
            timerStep.tv_sec = 0;
            timerStep.tv_usec = 1000 * 1000; // 1000 msec
            int res = event_add ( m_timer, &timerStep );

            if ( 0 != res )
                throw std::runtime_error ( "error adding timer event" );
        }

        m_connectionHandler.onConnected ();
    }

    ~InternalConnectionHandler()
    {
        if ( m_bev ) bufferevent_free ( m_bev );
        if ( m_timer ) event_free ( m_timer );
    }

    static void readCallback ( bufferevent* bev, void* arg )
    {
        TThisType *pThis = ( TThisType * ) arg;

        size_t n = bufferevent_read ( bev, & ( pThis->m_inBuffer[0] ), pThis->m_inBuffer.size() );

        try
        {
            pThis->m_connectionHandler.onRead ( & ( pThis->m_inBuffer[0] ), n );
        }
        catch ( const std::exception &e )
        {
            LogConfig::logger()->info ( "error in connection handler onRead: %s \nException:%s", pThis->m_connectionId.c_str(), e.what() );
            delete pThis;
        }
        catch ( ... )
        {
            LogConfig::logger()->info ( "unknown error in connection handler onRead: %s", pThis->m_connectionId.c_str() );
            delete pThis;
        }
    };

    static void writeCallback ( bufferevent* bev, void* arg )
    {
        TThisType *pThis = ( TThisType * ) arg;
    };

    static void eventCallback ( bufferevent* bev, short int events, void* arg )
    {
        TThisType *pThis = ( TThisType * ) arg;

        if ( ! ( events & BEV_EVENT_CONNECTED ) )
        {
            LogConfig::logger()->info ( "disconnected: %s", pThis->m_connectionId.c_str() );

            try
            {
                pThis->m_connectionHandler.onError();
            }
            catch ( const std::exception &e )
            {
                LogConfig::logger()->info ( "error in connection handler onError: %s", pThis->m_connectionId.c_str() );
            }
            catch ( ... )
            {
                LogConfig::logger()->info ( "unknown error in connection handler onError: %s", pThis->m_connectionId.c_str() );
            }

            delete pThis;
        }
        else
        {
            LogConfig::logger()->info ( "connected: %s", pThis->m_connectionId.c_str() );

            try
            {
                pThis->internalConnected();
            }
            catch ( const std::exception &e )
            {
                LogConfig::logger()->info ( "error in connection handler onConnected: %s", pThis->m_connectionId.c_str() );
                delete pThis;
            }
            catch ( ... )
            {
                LogConfig::logger()->info ( "unknown error in connection handler onConnected: %s", pThis->m_connectionId.c_str() );
                delete pThis;
            }
        }
    };


    static void timerCallback ( evutil_socket_t signal, short events, void* arg )
    {
        TThisType *pThis = ( TThisType * ) arg;

        try
        {
            pThis->m_connectionHandler.onTimer ();
        }
        catch ( const std::exception &e )
        {
            LogConfig::logger()->info ( "error in connection handler onTimer: %s", pThis->m_connectionId.c_str() );
            delete pThis;
        }
        catch ( ... )
        {
            LogConfig::logger()->info ( "unknown error in connection handler onTimer: %s", pThis->m_connectionId.c_str() );
            delete pThis;
        }
    }
};


/*
 * libevent listener wrapper
 *
 * creates TConnectionHandler on incoming connection
 * 
 * TConnectionHandler - have same methods as ConnectionHandlerBase and constructor (event_base* eventBase, OutBuffer &outBuffer, TConnectionHandlerParameter *state)
 * TConnectionHandlerParameter - parameter value for TConnectionHandler constructor
 *
 */

template<class TConnectionHandler, class TConnectionHandlerParameter>
class ConnectionListener
{
    event_base* m_eventBase;
    evconnlistener *m_listener;

    TConnectionHandlerParameter *m_connectionHandlerParameter;

    typedef ConnectionListener<TConnectionHandler, TConnectionHandlerParameter> TThisType;
    typedef InternalConnectionHandler<TConnectionHandler, TConnectionHandlerParameter> TInternalConnectionHandler;
public:
    ConnectionListener ( event_base* eventBase, uint16_t port, TConnectionHandlerParameter *connectionHandlerParameter, std::string parser) :
        m_eventBase ( eventBase ),
        m_listener ( 0 ),
        m_connectionHandlerParameter ( connectionHandlerParameter ),
        m_parser(parser)
    {
        sockaddr_in6 addr;
        ::memset ( &addr, 0, sizeof ( addr ) );
        addr.sin6_family = AF_INET6;
        addr.sin6_port = htons ( port );

        m_listener = evconnlistener_new_bind (
                         eventBase,
                         acceptCallback,
                         this,
                         LEV_OPT_CLOSE_ON_FREE | LEV_OPT_CLOSE_ON_EXEC | LEV_OPT_REUSEABLE,
                         -1, ( const sockaddr* ) &addr, sizeof ( addr ) );

        LogConfig::logger()->info ( "listening on %i", ( int ) port );
    }

    ~ConnectionListener()
    {
        if ( m_listener )
            evconnlistener_free ( m_listener );

        LogConfig::logger()->info ( "stop listening" );
    }

    static void acceptCallback ( struct evconnlistener* listener, evutil_socket_t sock, struct sockaddr* sockaddr_ptr, int socklen, void* arg )
    {
        TThisType *pThis = ( TThisType * ) arg;

        char straddr[INET6_ADDRSTRLEN];

        if ( sockaddr_ptr->sa_family == AF_INET6 )
        {
            struct sockaddr_in6 *inaddr_ptr = ( struct sockaddr_in6 * ) sockaddr_ptr;
            inet_ntop ( AF_INET6, & ( inaddr_ptr->sin6_addr ), straddr, sizeof ( straddr ) );
            LogConfig::logger()->info ( "connected %s:%i", straddr, inaddr_ptr->sin6_port );
        }
        else
            LogConfig::logger()->info ( "connected: unknown" );

        try
        {
            new TInternalConnectionHandler ( pThis->m_eventBase, sock, straddr, pThis->m_connectionHandlerParameter, pThis->m_parser);
        }
        catch ( const std::runtime_error &e )
        {
            LogConfig::logger()->error ( "connection init error %s", e.what() );
        }
    }
private:
    std::string m_parser;
};

/*
 * libevent connect wrapper
 *
 * creates TConnectionHandler on establishing connection
 * 
 * TConnectionHandler - have same methods as ConnectionHandlerBase and constructor (event_base* eventBase, OutBuffer &outBuffer, TConnectionHandlerParameter *state)
 * TConnectionHandlerParameter - parameter value for TConnectionHandler constructor
 *
 */

template<class TConnectionHandler, class TConnectionHandlerParameter>
class Connector
{
    evdns_base *m_dnsBase;
    event_base* m_eventBase;
    TConnectionHandlerParameter *m_connectionHandlerParameter;

    typedef Connector<TConnectionHandler, TConnectionHandlerParameter> TThisType;
    typedef InternalConnectionHandler<TConnectionHandler, TConnectionHandlerParameter> TInternalConnectionHandler;
public:
    Connector ( event_base*eventBase, uint16_t port, const char * host, TConnectionHandlerParameter *connectionHandlerParameter, std::string parser ="") :
        m_dnsBase ( 0 ),
        m_eventBase (eventBase ),
        m_connectionHandlerParameter ( connectionHandlerParameter )
    {
        m_dnsBase = evdns_base_new ( eventBase, 1 );
        if ( !m_dnsBase )
            throw std::runtime_error ( "dns error" );

        try
        {
            std::auto_ptr<TInternalConnectionHandler> ch ( new TInternalConnectionHandler ( m_eventBase, -1, host, m_connectionHandlerParameter, parser ) );
            if ( bufferevent_socket_connect_hostname ( ch->getBufferEvent(), m_dnsBase, AF_INET, host, port ) < 0 )
                throw std::runtime_error ( "error starting connecting" );

            ch.release();
        }
        catch ( const std::runtime_error &e )
        {
            LogConfig::logger()->error ( "connection init error %s", e.what() );
        }
       evdns_base_free(m_dnsBase,1);
    };
};

#endif // LIBEVENTWRAPPER_H
