#include <sstream>
#include <string>

#include <geos/geom/Point.h>
#include <geos/geom/LineString.h>
#include <geos/io/WKBReader.h>
#include <geos/io/WKBWriter.h>

#include "geometryeditor.h"
#include "maplayer.h"
#include "sharedptr.h"
#include "logconfig.h"
#include "pointsoperator.h"
#include "nm2proxy.h"


AbstractEditor::AbstractEditor(QObject* parent)
	: QObject(parent)
{

}


EditorPool::EditorPool(QObject* parent)
	: AbstractEditor(parent)
{

}


GeometryEditor::GeometryEditor(QObject* parent)
	: AbstractEditor(parent),
	  m_mutex(QMutex::Recursive),
	  m_isEditing(false),
	  m_f(new geos::geom::GeometryFactory()),
	  m_vertexRadius(10),
	  m_movingNode(-1)
{

}

void GeometryEditor::draw(QPainter& painter)
{
    painter.save();

    QPen pen(QColor(0, 250, 250));
    pen.setWidth(2);

    painter.setPen(pen);
    painter.setBrush(Qt::Dense4Pattern);

	m_mutex.lock();
    QPolygon polygon(m_currentPolygon->getExteriorRing()->getNumPoints());

    for (int i = 0, iend = m_currentPolygon->getExteriorRing()->getNumPoints(); i<iend;++i)
    {
        int end_pos = i;

		NM2Proxy_ScreenPoint endPt = NM2Proxy::instance()->pointToScreen(
                                m_currentPolygon->getExteriorRing()->getPointN(end_pos)->getX(),
                                m_currentPolygon->getExteriorRing()->getPointN(end_pos)->getY());

		polygon.setPoint(i, endPt.x(), endPt.y());
		painter.drawEllipse(endPt.x()-m_vertexRadius, endPt.y()-m_vertexRadius, m_vertexRadius*2, m_vertexRadius*2);
    }
	m_mutex.unlock();

    painter.drawPolygon(polygon);

	painter.restore();
}


bool GeometryEditor::mouseDown(const QPoint& pos)
{
	QMutexLocker loc(&m_mutex);

    if (!m_isEditing)
        return false;

	PointsOperator po(m_currentPolygon->getExteriorRing()->getCoordinates(), m_f);

    int ni = po.findScreenNode(pos, m_vertexRadius);
    if (ni >= 0) //start moving
    {
        m_movingNode = ni;
        return true;
    }

    return false;
}

bool GeometryEditor::mouseUp(const QPoint& pos)
{
	QMutexLocker loc(&m_mutex);

    if (!m_isEditing)
        return false;

    if (m_movingNode >= 0) //is moving?
    {
		PointsOperator po(m_currentPolygon->getExteriorRing()->getCoordinates(), m_f);
        po.setScreenNodeAt(m_movingNode, pos);
        m_currentPolygon = SharedPtr<geos::geom::Polygon>(po.getPolygon());
        m_movingNode = -1;
        return true;
    }

    return false;
}

bool GeometryEditor::mouseMove(const QPoint& pos)
{
	QMutexLocker loc(&m_mutex);

    if (!m_isEditing)
        return false;

    if (m_movingNode >= 0) //is moving?
    {
		PointsOperator po(m_currentPolygon->getExteriorRing()->getCoordinates(), m_f);
        po.setScreenNodeAt(m_movingNode, pos);
        m_currentPolygon = SharedPtr<geos::geom::Polygon>(po.getPolygon());
        return true;
    }
    return false;
}

void GeometryEditor::newFence(const int&x, const int&y)
{
	QMutexLocker loc(&m_mutex);

    startEditing("");
    resetCurrentPolygon(x,y);
}

void GeometryEditor::resetCurrentPolygon ( const int& x, const int& y )
{
	PointsOperator po(m_currentPolygon->getExteriorRing()->getCoordinates(), m_f);

    if (po.screenNodes.size() == 0) //if empty - create new
    {
        po.addScreenNodeAt(0, QPoint(x+40, y+40));
        po.addScreenNodeAt(0, QPoint(x+40, y-40));
        po.addScreenNodeAt(0, QPoint(x-40, y-40));
        po.addScreenNodeAt(0, QPoint(x-40, y+40));
        po.addScreenNodeAt(0, QPoint(x+40, y+40));
        m_currentPolygon = SharedPtr<geos::geom::Polygon>(po.getPolygon());
    }
}

bool GeometryEditor::mouseDoubleClick(const QPoint& pos)
{
	QMutexLocker loc(&m_mutex);

    if (!m_isEditing)
        return false;

    if (m_currentPolygon->getLength() == 0)
    {
        //some fallback code
        resetCurrentPolygon(pos.x(), pos.y());
        return true;
    }

    m_movingNode = -1;

    //check if on node
	PointsOperator po(m_currentPolygon->getExteriorRing()->getCoordinates(), m_f);

    int ni = po.findScreenNode(pos, m_vertexRadius);
    if (ni >=0) //delete node
    {
        if (po.screenNodes.size()==4)
            return true;

        po.deleteAt(ni);
        m_currentPolygon = SharedPtr<geos::geom::Polygon>(po.getPolygon());
        return true;
    }

    int li = po.findScreenLine(pos, m_vertexRadius);
    if (li >=0) //add node
    {
        po.addScreenNodeAt(li, pos);
        m_currentPolygon = SharedPtr<geos::geom::Polygon>(po.getPolygon());
        return true;
    }

    return false;
}

bool GeometryEditor::isEditing()
{
    return m_isEditing;
}

void GeometryEditor::startEditing(QString strHexWKB)
{
	QMutexLocker loc(&m_mutex);
    m_isEditing = true;
    m_movingNode = -1;
    emit editStateChanged(true);
    m_currentPolygon =  SharedPtr<geos::geom::Polygon>(m_f->createPolygon());
    applyCurentPolygon(strHexWKB);
}

void GeometryEditor::applyCurentPolygon(QString strHexWKB)
{
	//try decode
	if (strHexWKB.length()>0) {
		try {
			std::string strBuf = strHexWKB.toStdString();
			std::stringbuf sb(strBuf);
			std::istream istr(&sb);
			geos::io::WKBReader reader(*m_f);
			SharedPtr<geos::geom::Geometry> pGeom(reader.readHEX(istr));

			if (pGeom->getGeometryTypeId() == geos::geom::GEOS_POLYGON)
				m_currentPolygon = pGeom;

		} catch (std::exception &e)	{
			qDebug() << "WKB parsing std exception: "<<e.what()<<endl;
			LogConfig::logger()->error("WKB parsing std exception: \r\n %s", e.what());
		}
	}
}

QString GeometryEditor::stopEditing()
{
    emit editStateChanged(false);

	QMutexLocker loc(&m_mutex);
    m_movingNode = -1;
    if (m_isEditing)
    {
        //encode
        m_isEditing = false;
        geos::io::WKBWriter writer;
        std::ostringstream ostr;
        writer.writeHEX(*m_currentPolygon.getAddr(), ostr);

        std::string hexStr = ostr.str();
        QString qhexStr = QString::fromAscii(hexStr.c_str());
        return  qhexStr;
    }

    return QString();
}

void GeometryEditor::showGeometry()
{
    geos::geom::Coordinate coord;

	QMutexLocker loc(&m_mutex);
	if (m_currentPolygon->getCentroid(coord)) {
        emit movedTo(coord.x, coord.y);
	}
}

void GeometryEditor::showGeometry(QString strHexWKB)
{
	QMutexLocker loc(&m_mutex);
	applyCurentPolygon(strHexWKB);
    showGeometry();
}

//--------------------------------------------------------------------------------------
GeometryEditorPool::GeometryEditorPool(QObject* parent)
	: EditorPool(parent)
{

}

GeometryEditorPool::~GeometryEditorPool()
{
	for (int i=0; i<m_Editors.size(); i++) {
		delete m_Editors[i];
	}
}

bool GeometryEditorPool::isEditing()
{
	for (int i = 0; i < m_Editors.size(); i++) {
		if (m_Editors[i]->isEditing()) {
			return true;
		}
	}

	return false;
}

GeometryEditor* GeometryEditorPool::getEditor(MapScene*)
{
    for (int i=0; i<m_Editors.size();i++)
        if (!m_Editors[i]->isEditing())
            return m_Editors[i].data();

	QPointer<GeometryEditor> newEditor(new GeometryEditor(0));
    m_Editors.push_back(newEditor);

    return newEditor.data();
}

void GeometryEditorPool::draw(QPainter& painter)
{
    for (int i=0; i<m_Editors.size();i++)
        if (m_Editors[i]->isEditing())
			m_Editors[i]->draw(painter);

}

bool GeometryEditorPool::mouseDoubleClick(const QPoint& pos)
{
    for (int i=0; i<m_Editors.size();i++)
        if (m_Editors[i]->isEditing())
            if (m_Editors[i]->mouseDoubleClick(pos))
                return true;

    return false;
}

bool GeometryEditorPool::mouseDown(const QPoint& pos)
{
    for (int i=0; i<m_Editors.size();i++)
        if (m_Editors[i]->isEditing())
            if (m_Editors[i]->mouseDown(pos))
                return true;

    return false;
}

bool GeometryEditorPool::mouseMove(const QPoint& pos)
{
    for (int i=0; i<m_Editors.size();i++)
        if (m_Editors[i]->isEditing())
            if (m_Editors[i]->mouseMove(pos))
                return true;

    return false;
}

bool GeometryEditorPool::mouseUp(const QPoint& pos)
{
    for (int i=0; i<m_Editors.size();i++)
        if (m_Editors[i]->isEditing())
            if (m_Editors[i]->mouseUp(pos))
                return true;

    return false;
}
