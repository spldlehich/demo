---
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import org.apache.commons.lang3.StringUtils;
---

/**
 * <code>TaskExecutor</code> исполняет заданные команды. Использует @ProcessBuilder. Обрабатывает системные стримы для
 * вывода и везвращает содержимое. не следует пытаться менять поведение данного класса или наследоваться от него, т.к.
 * он предназначен исключительно для обработки задач, которые создают реализации IOsInfo.
 *
 * <p>
 * Вариант использования:
 *
 * <pre>
 * TaskExecutor taskExecutor = new TaskExecutor(executor);
 * List&lt;String&gt; lines = taskExecutor.getResultLines();
 * </pre>
 *
 */
public final class TaskExecutor
{
    private final Executor executor;
    private List<String> resultLines = new LinkedList<>();

    /**
     * Конструктор.
     * 
     * @param executor исполнитель
     */
    public TaskExecutor(Executor executor)
    {
        super();
        this.executor = executor;
    }

    /**
     * Возвращает результат вывода
     * 
     * @return список строк
     */
    public final List<String> getResultLines()
    {
        return resultLines;
    }

    /**
     * Выполняет команду
     * 
     * @param cmdArray массив команды
     * @throws ComputerInfoException при ошибке обработки задачи
     */
    public final void execute(String[] cmdArray) throws ComputerInfoException
    {
        try
        {
            CountDownLatch latch = new CountDownLatch(2);
            Process process = new ProcessBuilder(cmdArray).start();
            StreamReader sysOut = new StreamReader(process.getInputStream(), latch);
            StreamReader sysError = new StreamReader(process.getErrorStream(), latch);
            executor.execute(sysOut);
            executor.execute(sysError);
            int exitValue = process.waitFor();
            if (exitValue != 0)
                throw new ComputerInfoException(sysError.getMessages());
            latch.await();
            resultLines = sysOut.getLines();
        }
        catch (IOException e)
        {
            throw new ComputerInfoException(Messages.information_exception(), e);
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            throw new ComputerInfoException(Messages.information_exception(), e);
        }
    }

    private class StreamReader implements Runnable
    {
        private final InputStream is;
        private List<String> lines = new LinkedList<>();
        private CountDownLatch latch;

        public StreamReader(InputStream is, CountDownLatch latch)
        {
            super();
            this.is = is;
            this.latch = latch;
        }

        public List<String> getLines()
        {
            return lines;
        }

        public String getMessages()
        {
            return StringUtils.join(lines, System.lineSeparator());
        }

        @Override
        public void run()
        {
            // Считываем вывод
            try (Scanner scan = new Scanner(is))
            {
                while (scan.hasNextLine())
                {
                    String line = scan.nextLine();
                    lines.add(line);
                }
            }
            finally
            {
                // Чтение закончено
                latch.countDown();
            }
        }
    }
}
