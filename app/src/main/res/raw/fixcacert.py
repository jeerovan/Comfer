import sys

# --- Configuration ---
# The name of the corrupted input file.
input_filename = 'cacert.pem'
# The name of the file where the cleaned output will be saved.
output_filename = 'cacert.pem.fixed'
# ---------------------

def fix_cacert_file(input_file, output_file):
    """
    Reads a PEM file and extracts only the certificate blocks.

    Args:
        input_file (str): The path to the source PEM file.
        output_file (str): The path to write the cleaned PEM file.
    """
    try:
        with open(input_file, 'r') as infile:
            # A flag to track if we are currently inside a certificate block.
            in_certificate_block = False
            # A list to hold the lines that should be kept.
            certificate_lines = []

            for line in infile:
                # Use strip() to remove leading/trailing whitespace for comparison.
                stripped_line = line.strip()

                if stripped_line == '-----BEGIN CERTIFICATE-----':
                    # We have entered a certificate block.
                    in_certificate_block = True
                
                if in_certificate_block:
                    # If we are inside a block, keep the line if it's not empty.
                    if stripped_line:
                        certificate_lines.append(line)
                
                if stripped_line == '-----END CERTIFICATE-----':
                    # We have reached the end of the block.
                    in_certificate_block = False
            
            if not certificate_lines:
                print(f"Warning: No certificate blocks were found in '{input_file}'.")
                return

            # Write the cleaned lines to the output file.
            with open(output_file, 'w') as outfile:
                outfile.writelines(certificate_lines)
            
            print(f"Successfully processed '{input_file}'.")
            print(f"Cleaned content has been saved to '{output_file}'.")

    except FileNotFoundError:
        print(f"Error: The file '{input_file}' was not found.")
        print("Please make sure the script is in the same directory as your file, or provide the full path.")
        sys.exit(1)
    except Exception as e:
        print(f"An unexpected error occurred: {e}")
        sys.exit(1)

# --- Main execution ---
if __name__ == "__main__":
    fix_cacert_file(input_filename, output_filename)

