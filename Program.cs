using System;

class Program
{
    static void Main()
    {
        string[] choices = { "Kéo", "Búa", "Bao" };
        Random random = new Random();

        while (true)
        {
            Console.Clear();
            Console.WriteLine("=== GAME KÉO - BÚA - BAO ===");
            Console.WriteLine("0 - Kéo");
            Console.WriteLine("1 - Búa");
            Console.WriteLine("2 - Bao");
            Console.Write("Chọn của bạn (0-2, hoặc q để thoát): ");
            string input = Console.ReadLine();

            if (input.ToLower() == "q") break;

            if (!int.TryParse(input, out int playerChoice) || playerChoice < 0 || playerChoice > 2)
            {
                Console.WriteLine("Lựa chọn không hợp lệ!");
                Console.ReadKey();
                continue;
            }

            int computerChoice = random.Next(0, 3);

            Console.WriteLine($"Bạn chọn: {choices[playerChoice]}");
            Console.WriteLine($"Máy chọn: {choices[computerChoice]}");

            // Xử lý kết quả
            if (playerChoice == computerChoice)
                Console.WriteLine("Hòa!");
            else if ((playerChoice == 0 && computerChoice == 2) ||
                     (playerChoice == 1 && computerChoice == 0) ||
                     (playerChoice == 2 && computerChoice == 1))
                Console.WriteLine("Bạn thắng!");
            else
                Console.WriteLine("Bạn thua!");

            Console.WriteLine("\nNhấn phím bất kỳ để chơi tiếp...");
            Console.ReadKey();
        }
    }
}
