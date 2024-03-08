using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;
using System.Drawing;
using System.Windows.Forms;
namespace OnlyR.Core.Recorder
{
    public class Overlay : Form
    {
        private Label resultLabel;

        public Overlay()
        {
            InitializeComponent();
        }

        private void InitializeComponent()
        {
            this.resultLabel = new Label();
            this.SuspendLayout();

            // Overlay window settings
            this.TopMost = true;
            this.FormBorderStyle = FormBorderStyle.None;
            this.BackColor = Color.Black;
            this.Opacity = 0.8;
            this.StartPosition = FormStartPosition.Manual;
            this.Location = new Point(Screen.PrimaryScreen.WorkingArea.Width - this.Width,
                                      Screen.PrimaryScreen.WorkingArea.Height - this.Height);

            // Label settings
            this.resultLabel.AutoSize = true;
            this.resultLabel.ForeColor = Color.White;
            this.resultLabel.Location = new Point(5, 5);
            this.resultLabel.Text = "Inference Result: ";

            // Add the label to the form
            this.Controls.Add(this.resultLabel);
            this.ResumeLayout(false);

            // Adjust the size of the form to fit the label
            this.Size = new Size(this.resultLabel.Width + 20, this.resultLabel.Height + 20);
        }
        public void UpdateOverlay(string text)
        {
            if (this.InvokeRequired)
            {
                this.Invoke(new Action<string>(UpdateOverlay), text);
            }
            else
            {
                this.resultLabel.Text = $"Inference Result: {text}";
                this.AdjustFormSize();
            }
        }
        public void UpdateResult(string text)
        {
            this.resultLabel.Text = $"Inference Result: {text}";
            this.AdjustFormSize();
        }

        private void AdjustFormSize()
        {
            this.Size = new Size(this.resultLabel.Width + 20, this.resultLabel.Height + 20);
            this.Location = new Point(Screen.PrimaryScreen.WorkingArea.Width - this.Width,
                                      Screen.PrimaryScreen.WorkingArea.Height - this.Height);
        }
    }
}
