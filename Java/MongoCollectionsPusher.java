---
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
---
import com.mongodb.MongoBulkWriteException;
import com.mongodb.MongoException;
import com.mongodb.bulk.BulkWriteError;
import com.mongodb.bulk.BulkWriteResult;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.BulkWriteOptions;
import com.mongodb.client.model.InsertOneModel;
import com.mongodb.client.model.UpdateManyModel;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.WriteModel;

/**
 * Предназначен для вставки документов в коллекцию с обработкой исключений.
 * <p>
 * Вариант использования:
 *
 * <pre>
 * MongoCollectionsPusher pusher = new MongoCollectionsPusher();
 * pusher.push(collect, documents);
 * </pre>
 */
public class MongoCollectionsPusher
{
    private static final Logger LOGGER = LoggerFactory.getLogger(MongoCollectionsPusher.class);
    private static final String MONGO_UPDATE_COMMAND = "$set";

    /**
     * Вставка документов с возможностью дублирования документов
     * 
     * @param collect коллекция
     * @param documents документы
     * @return флаг об успешном выполнении
     * @throws MongoPusherException исключение, при некорректной вставке
     */
    public boolean push(MongoCollection<Document> collect, List<InsertOneModel<Document>> documents)
            throws MongoPusherException
    {
        BulkWriteResult result = null;
        if (documents == null || documents.isEmpty())
            return true;
        try
        {
            LOGGER.debug(String.format("Push into %s %s", collect.getNamespace(), new Date().getTime()));
            result = collect.bulkWrite(documents, new BulkWriteOptions().ordered(true));
            documents.clear();
        }
        catch (MongoBulkWriteException e)
        {
            List<BulkWriteError> errors = e.getWriteErrors();
            LOGGER.warn(e.getMessage());
            if (errors != null)
                for (BulkWriteError error : errors)
                    LOGGER.warn(e.getMessage() + error.getMessage());
            if (result == null)
                result = e.getWriteResult();
            if (result != null && (result.getInsertedCount() > 0))
            {
                Iterator<InsertOneModel<Document>> it = documents.iterator();
                for (int i = 0; it.hasNext() && (i < result.getInsertedCount()); ++i)
                {
                    it.next();
                    it.remove();
                }
            }
        }
        catch (MongoException e)
        {
            throw new MongoPusherException(Messages.push_exception(), e);
        }
        return true;
    }

    /**
     * Вставка документов исключающая возможность дублирования документов
     * 
     * @param collect коллекция
     * @param documents документы
     * @return флаг об успешном выполнении
     * @throws MongoPusherException исключение, при некорректной вставке
     */
    public boolean pushWithoutDuplicate(MongoCollection<Document> collect, List<InsertOneModel<Document>> documents)
            throws MongoPusherException
    {
        for (InsertOneModel<Document> doc : documents)
        {
            if (doc == null || doc.getDocument() == null || doc.getDocument().get("_id") == null)
            {
                LOGGER.debug("pushWithoutDuplicate : (doc == null || doc.getDocument() == null || doc.getDocument().get(\"_id\") == null)");
                continue;
            }
            Document docReplace = new Document();
            docReplace.put("_id", doc.getDocument().get("_id"));
            try
            {
                doc.getDocument().remove("_id");
                collect.updateOne(docReplace, new Document(MONGO_UPDATE_COMMAND, doc.getDocument()),
                        new UpdateOptions().upsert(true));
            }
            catch (MongoException e)
            {
                throw new MongoPusherException(Messages.push_exception(), e);
            }
        }
        documents.clear();
        return true;
    }

    /**
     * Обновление статусов транзакций
     * 
     * @param collect коллекция
     * @param elements элементы
     * @return
     * @throws MongoPusherException исключение, при некорректном апдейте
     */
    public boolean updateTransactions(MongoCollection<Document> collect, Set<TransactionUpdateElement> elements)
            throws MongoPusherException
    {
        List<WriteModel<Document>> updates = new ArrayList<>(elements.size());
        UpdateOptions updateOptions = new UpdateOptions().upsert(false);
        for (TransactionUpdateElement element : elements)
        {
            if (element == null || element.getId() == null)
                continue;
            Document docReplace = new Document();
            docReplace.put(LoggedNames.TRANSACTION_ID_NAME_LEGEND, element.getId());
            Document docUpdate = new Document();
            docUpdate.put(LoggedNames.TRANSACTION_STATUS_NAME_LEGEND, element.getStatus());
            updates.add(new UpdateManyModel<Document>(docReplace, new Document(MONGO_UPDATE_COMMAND, docUpdate),
                    updateOptions));
        }
        bulkUpdate(collect, updates);
        elements.clear();
        return false;
    }

    private void bulkUpdate(MongoCollection<Document> collect, List<WriteModel<Document>> updates)
            throws MongoPusherException
    {
        try
        {
            collect.bulkWrite(updates);
        }
        catch (MongoException e)
        {
            throw new MongoPusherException(Messages.update_transaction_exception(), e);
        }
    }

    @Localizable
    interface IMessagesList
    {
        IMessagesList Messages = LocalizableFactory.create(IMessagesList.class);

        @DefaultString("Cannot write in log events database.")
        @Context("Ошибка записи событий.")
        @Tags({"logs"})
        String push_exception();
        
        @DefaultString("Cannot update transaction status.")
        @Context("Ошибка обновления данных о транзакциях.")
        @Tags({"logs"})
        String update_transaction_exception();
    }
}
